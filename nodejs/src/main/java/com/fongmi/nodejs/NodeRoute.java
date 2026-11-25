package com.fongmi.nodejs;

/**
 * node: 路由工具——TVBox 源 API 里 "node:/path?query" 形态与 Node 服务端路由的拼装/解析。
 *
 * <p>append(api, endpoint)：在源 api 路径后追加子端点（如 /home /search /play），
 * 保留 query 参数、剥掉 fragment，统一斜杠规范；path(api)：取出纯路径供 HTTP 请求使用。
 * 所有 Spider 业务方法都经这两个方法把调用映射到 Node 服务的具体路由上。</p>
 */
public final class NodeRoute {

    private static final String PREFIX = "node:";

    private NodeRoute() {
    }

    public static String append(String api, String endpoint) {
        if (api == null || !api.startsWith(PREFIX)) throw new IllegalArgumentException("Invalid Node API");
        String value = api.substring(PREFIX.length());
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        int query = value.indexOf('?');
        String suffix = query < 0 ? "" : value.substring(query);
        String path = query < 0 ? value : value.substring(0, query);
        if (!path.startsWith("/")) path = "/" + path;
        while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        String child = endpoint == null ? "" : endpoint.trim();
        if (!child.isEmpty() && !child.startsWith("/")) child = "/" + child;
        if (path.equals("/") && !child.isEmpty()) path = "";
        return PREFIX + path + child + suffix;
    }

    public static String path(String api) {
        if (api == null || !api.startsWith(PREFIX)) throw new IllegalArgumentException("Invalid Node API");
        String route = api.substring(PREFIX.length());
        return route.startsWith("/") ? route : "/" + route;
    }
}
