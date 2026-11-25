package com.github.catvod.net;

import androidx.annotation.NonNull;

import com.github.catvod.utils.Auth;

import java.io.IOException;
import java.net.ProtocolException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class ProxyRedirectInterceptor implements Interceptor {

    private static final int MAX_FOLLOW_UPS = 20;

    private final OkProxySelector selector;
    // 含本拦截器的客户端均为 followRedirects(false)、followSslRedirects 保持默认 true（noRedirect 会移除本拦截器）
    private final boolean followSslRedirects;

    ProxyRedirectInterceptor(OkProxySelector selector) {
        this(selector, true);
    }

    ProxyRedirectInterceptor(OkProxySelector selector, boolean followSslRedirects) {
        this.selector = selector;
        this.followSslRedirects = followSslRedirects;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        OkProxySelector.Policy previousPolicy = null;
        Response previousResponse = null;
        int followUpCount = 0;
        while (true) {
            OkProxySelector.Policy policy = selectPolicy(request, previousPolicy);
            request = withPolicy(request, policy, previousPolicy);
            Response response = policy == null ? chain.proceed(request) : routedCall(chain, policy, request);
            followUpCount += countFollowUps(response);
            response = withPriorResponse(response, previousResponse);
            checkFollowUps(response, followUpCount);
            NetOptions options = request.tag(NetOptions.class);
            if (options != null && !options.isRedirect()) return response;
            Request followUp = redirect(response, followSslRedirects);
            if (followUp == null) return response;
            RequestBody body = followUp.body();
            if (body != null && body.isOneShot()) return response;
            response.close();
            if (++followUpCount > MAX_FOLLOW_UPS) throw new ProtocolException("Too many follow-up requests: " + followUpCount);
            previousResponse = response.newBuilder().body(ResponseBody.create(null, new byte[0])).build();
            previousPolicy = policy;
            request = followUp;
        }
    }

    private OkProxySelector.Policy selectPolicy(Request request, OkProxySelector.Policy previous) {
        // 已处于 routedCall 的策略覆盖中（嵌套执行）时不再重复选择
        if (OkProxySelector.override() != null) return null;
        return selector.policy(request.url(), previous);
    }

    // okhttp 4.x 无 Chain.withProxySelector()：用线程级策略覆盖 + 原链继续执行，保留原客户端的超时/拦截器配置
    private Response routedCall(Chain chain, OkProxySelector.Policy policy, Request request) throws IOException {
        OkProxySelector.Policy previous = OkProxySelector.override(policy);
        try {
            return chain.proceed(request);
        } finally {
            OkProxySelector.override(previous);
        }
    }

    private Request withPolicy(Request request, OkProxySelector.Policy policy, OkProxySelector.Policy previous) {
        if (policy == null) return request;
        Request.Builder builder = request.newBuilder();
        if (previous != null && !policy.sharesProxyCredentials(previous)) builder.removeHeader("Proxy-Authorization");
        return builder.tag(OkProxySelector.Policy.class, policy).build();
    }

    private int countFollowUps(Response response) {
        int count = 0;
        for (Response prior = response.priorResponse(); prior != null; prior = prior.priorResponse()) count++;
        return count;
    }

    private void checkFollowUps(Response response, int count) throws ProtocolException {
        if (count <= MAX_FOLLOW_UPS) return;
        response.close();
        throw new ProtocolException("Too many follow-up requests: " + count);
    }

    private Response withPriorResponse(Response response, Response previous) {
        if (previous == null) return response;
        Response prior = response.priorResponse();
        return response.newBuilder().priorResponse(prior == null ? previous : withPriorResponse(prior, previous)).build();
    }

    private Request redirect(Response response, boolean followSslRedirects) {
        HttpUrl target = redirectUrl(response, followSslRedirects);
        if (target == null) return null;
        Request.Builder builder = response.request().newBuilder();
        redirectMethod(builder, response.request(), response.code());
        if (!sameOrigin(response.request().url(), target)) builder.removeHeader("Authorization");
        Request redirected = builder.url(target).build();
        Auth.Context auth = redirected.tag(Auth.Context.class);
        return auth == null ? redirected : OkHttp.applyAuth(redirected, auth);
    }

    private HttpUrl redirectUrl(Response response, boolean followSslRedirects) {
        int code = response.code();
        if (code != 300 && code != 301 && code != 302 && code != 303 && code != 307 && code != 308) return null;
        String location = response.header("Location");
        if (location == null) return null;
        HttpUrl source = response.request().url();
        HttpUrl target = source.resolve(location);
        if (target == null || !source.scheme().equals(target.scheme()) && !followSslRedirects) return null;
        return target;
    }

    private void redirectMethod(Request.Builder builder, Request request, int code) {
        String method = request.method();
        if ("GET".equals(method) || "HEAD".equals(method)) return;
        if (redirectsToGet(method, code)) builder.method("GET", null).removeHeader("Transfer-Encoding").removeHeader("Content-Length").removeHeader("Content-Type");
        else builder.method(method, request.body());
    }

    private boolean sameOrigin(HttpUrl source, HttpUrl target) {
        return source.scheme().equals(target.scheme()) && source.host().equals(target.host()) && source.port() == target.port();
    }

    private boolean redirectsToGet(String method, int code) {
        if (code == 303) return !"PROPFIND".equals(method);
        if (code == 307 || code == 308) return false;
        return !"PROPFIND".equals(method) && !"QUERY".equals(method);
    }
}
