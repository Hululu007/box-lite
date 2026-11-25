package com.orhanobut.hawk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * box-lite v1.1.8：修复 Hawk 2.0.1 与 Gson 2.10+ 的不兼容。
 *
 * 根因：原 HawkConverter.toList/toMap/toSet 使用 new TypeToken<List<T>>(){}
 * 匿名类技巧携带方法类型变量（HawkConverter$1/$2/$3 extends TypeToken<Set<T>> 等），
 * Gson 2.10.1 起 TypeToken 构造器严格校验"类型参数不得包含类型变量"，直接抛
 * IllegalArgumentException → DefaultHawkFacade.get 捕获后静默返回默认值，
 * 表现为：所有 Map/List 类 Hawk 键"写得进、读出永远是空默认值"
 * （STORE_API_MAP / API_MAP / *_NAME_HISTORY / PROXY_URL_HISTORY 全部中招），
 * String 键不受影响。1.0.26 用的 gson 2.8.7 无此校验，故正常。
 *
 * 本 Converter 与原版行为一致，仅把 toList/toMap/toSet 改为
 * TypeToken.getParameterized(Class 实参...)（gson 2.8.3+ 官方 API，两个版本均可用）。
 * 序列化格式不变（Serializer 层未动），新旧版本数据互读。
 */
public final class FixedHawkConverter implements Converter {
    private final Parser parser;

    public FixedHawkConverter() {
        this.parser = new GsonParser(new Gson());
    }

    public FixedHawkConverter(Parser parser) {
        HawkUtils.checkNull("Parser", parser);
        this.parser = parser;
    }

    @Override
    public <T> String toString(T value) {
        if (value == null) return null;
        return parser.toJson(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> T fromString(String text, DataInfo info) throws Exception {
        if (text == null) return null;
        HawkUtils.checkNull("data info", info);
        Class<?> keyClazz = info.keyClazz;
        Class<?> valueClazz = info.valueClazz;
        switch (info.dataType) {
            case '0': { // object
                return (T) parser.fromJson(text, keyClazz != null ? keyClazz : Object.class);
            }
            case '1': { // list：keyClazz 为元素类型
                if (keyClazz == null) return (T) new ArrayList();
                Type type = TypeToken.getParameterized(ArrayList.class, keyClazz).getType();
                return (T) parser.fromJson(text, type);
            }
            case '2': { // map：keyClazz/valueClazz 为键值类型
                if (keyClazz == null || valueClazz == null) return (T) new HashMap();
                Type type = TypeToken.getParameterized(HashMap.class, keyClazz, valueClazz).getType();
                return (T) parser.fromJson(text, type);
            }
            case '3': { // set：keyClazz 为元素类型
                if (keyClazz == null) return (T) new HashSet();
                Type type = TypeToken.getParameterized(HashSet.class, keyClazz).getType();
                return (T) parser.fromJson(text, type);
            }
            default:
                return null;
        }
    }
}
