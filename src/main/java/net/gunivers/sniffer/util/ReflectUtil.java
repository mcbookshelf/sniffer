package net.gunivers.sniffer.util;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight utility for fast reflective access using VarHandle, MethodHandle and LambdaMetafactory.
 *
 * <p>Usage examples:
 * <pre>
 * // VarHandle read/write
 * VarHandle vh = ReflectUtil.findVarHandle(MyClass.class, "value", int.class);
 * int v = (int) ReflectUtil.vhGet(vh, myObj);
 * ReflectUtil.vhSet(vh, myObj, 123);
 *
 * // Method handle invoke
 * MethodHandle mh = ReflectUtil.findMethodHandle(MyClass.class, "compute", int.class);
 * Object result = ReflectUtil.invoke(mh, myObj, 5);
 * </pre>
 *
 */
@SuppressWarnings("unused")
public final class ReflectUtil {
    private ReflectUtil() {}
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.lookup();
    private static final Map<String, VarHandle> VAR_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> MH_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> CONSTRUCTOR_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Object> LAMBDA_CACHE = new ConcurrentHashMap<>();
    private static final MappingResolver MR = FabricLoader.getInstance().getMappingResolver();

    private static String mapField(String named, Class<?> owner, Class<?> target) {
        return MR.mapFieldName("named", owner.getName(), named, target.getName());
    }

    private static String key(Class<?> c, String name, Class<?>... types) {
        StringBuilder sb = new StringBuilder(c.getName()).append('#').append(name);
        for (Class<?> t : types) sb.append(':').append(t == null ? "null" : t.getName());
        return sb.toString();
    }

    private static String keyConstructor(Class<?> target, Class<?>... paramTypes) {
        StringBuilder sb = new StringBuilder(target.getName()).append("#<init>");
        for (Class<?> t : paramTypes) sb.append(':').append(t == null ? "null" : t.getName());
        return sb.toString();
    }

    //region variable

    /**
     * Find and cache varhandle in a class.
     * @param target  the class to look up the varhandle
     * @param fieldName  the name of the field to look up
     * @param fieldType  the type of the field to look up
     * @return the varhandle if found, or null if not found
     */
    @Nullable
    private static VarHandle findVarHandle(Class<?> target, String fieldName, Class<?> fieldType) {
        String k = key(target, fieldName);
        return VAR_HANDLE_CACHE.computeIfAbsent(k, kk -> {
            try {
                MethodHandles.Lookup lookup =
                        MethodHandles.privateLookupIn(target, PUBLIC_LOOKUP);
                return lookup.findVarHandle(target, fieldName, fieldType);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to find varhandle for field {} in class {}", fieldName, target, e);
                return null;
            }
        });
    }

    /**
     * Find and cache varhandle in a class without specifying the field type.
     * @param target  the class to look up the varhandle
     * @param fieldName  the name of the field to look up
     * @return the varhandle if found, or null if not found
     */
    @Nullable
    private static VarHandle findVarHandle(Class<?> target, String fieldName) {
        String k = key(target, fieldName);
        return VAR_HANDLE_CACHE.computeIfAbsent(k, kk -> {
            try {
                // 先通过反射获取字段类型
                Field field = target.getDeclaredField(fieldName);
                Class<?> fieldType = field.getType();
                MethodHandles.Lookup lookup =
                        MethodHandles.privateLookupIn(target, PUBLIC_LOOKUP);
                return lookup.findVarHandle(target, fieldName, fieldType);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to find varhandle for field {} in class {}", fieldName, target, e);
                return null;
            }
        });
    }

    /**
     * Get a field in a class using a varhandle
     * @param vh a varhandle, which should obtained from {@link ReflectUtil#findVarHandle(Class, String, Class)}
     * @param receiver the object to get the field value from
     * @return the value of the field
     */
    private static Object vhGet(VarHandle vh, Object receiver) {
        return vh.get(receiver);
    }

    /**
     * Check if a field exists in a class
     * @param object the object to check the field existence from
     * @param fieldName the name of the field to check
     * @param fieldType the type of the field to check
     * @return whether the field exists or not
     */
    public static boolean exist(@NotNull Object object, String fieldName, Class<?> fieldType) {
        return findVarHandle(object.getClass(), fieldName, fieldType) != null;
    }


    /**
     * Check if a field exists in a class
     * @param object the object to check the field existence from
     * @param fieldName the name of the field to check
     * @return whether the field exists or not
     */
    public static boolean exist(@NotNull Object object, String fieldName) {
        return findVarHandle(object.getClass(), fieldName) != null;
    }

    /**
     * Get a field in a class
     * @param object the object to get the field value from
     * @param fieldName the name of the field to get
     * @param fieldType the type of the field to get
     * @return the value of the field
     */
    public static Result<?> get(Object object, String fieldName, Class<?> fieldType){
        var vh = findVarHandle(object.getClass(), fieldName, fieldType);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            return Result.success(vhGet(vh, object));
        }
    }

    /**
     * Get a field in a class
     * @param object the object to get the field value from
     * @param fieldName the name of the field to get
     * @return the value of the field
     */
    public static Result<?> get(Object object, String fieldName){
        var vh = findVarHandle(object.getClass(), fieldName);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            return Result.success(vhGet(vh, object));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> getT(Object object, String fieldName, Class<T> fieldType) {
        var vh = findVarHandle(object.getClass(), fieldName, fieldType);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            return Result.success((T) vhGet(vh, object));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> getT(Object object, String fieldName) {
        var vh = findVarHandle(object.getClass(), fieldName);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            return Result.success((T) vhGet(vh, object));
        }
    }
    
    /**
     * Set a field in a class using a varhandle
     * @param vh a varhandle, which should obtained from {@link ReflectUtil#findVarHandle(Class, String, Class)}
     * @param receiver the object to set the field value to
     * @param value the value to set
     */
    private static void vhSet(VarHandle vh, Object receiver, Object value) {
        vh.set(receiver, value);
    }

    /**
     * Set a field in a class
     * @param object the object to set the field value to
     * @param fieldName the name of the field to set
     * @param fieldType the type of the field to set
     * @param value the value to set
     * @return success if the field was set, failed if not
     */
    public static Result<?> set(Object object, String fieldName, Class<?> fieldType, Object value) {
        var vh = findVarHandle(object.getClass(), fieldName, fieldType);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            vhSet(vh, object, value);
            return Result.success();
        }
    }

    public static Result<?> set(Object object, String fieldName, Object value) {
        var vh = findVarHandle(object.getClass(), fieldName);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            vhSet(vh, object, value);
            return Result.success();
        }
    }

    public static <T> Result<?> setT(Object object, String fieldName, Class<T> fieldType, T value) {
        var vh = findVarHandle(object.getClass(), fieldName, fieldType);
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            vhSet(vh, object, value);
            return Result.success();
        }
    }

    public static <T> Result<?> setT(Object object, String fieldName, T value) {
        var vh = findVarHandle(object.getClass(), fieldName, value.getClass());
        if(vh == null){
            return Result.failure("Field not found" + fieldName + " in " + object.getClass(), ExceptionCode.FIELD_NOT_FOUND);
        }else{
            vhSet(vh, object, value);
            return Result.success();
        }
    }

    //endregion

    //region method

    /**
     * Find and cache methodhandle in a class.
     * @param target  the class to look up the methodhandle
     * @param name  the name of the method to look up
     * @param paramTypes  the types of the parameters of the method to look up
     * @return the methodhandle if found, or null if not found
     */
    @Nullable
    private static MethodHandle findMethodHandle(Class<?> target, String name, Class<?>... paramTypes) {
        String k = key(target, name, paramTypes);
        return MH_HANDLE_CACHE.computeIfAbsent(k, kk -> {
            try {
                Method m = findMethodReflective(target, name, paramTypes);
                if (m == null) {
                    LOGGER.error("Method not found: {}({})", name, Arrays.toString(paramTypes));
                    return null;
                }
                m.setAccessible(true);
                return PUBLIC_LOOKUP.unreflect(m);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Error while looking up method: {}({})", name, Arrays.toString(paramTypes), e);
                return null;
            }
        });
    }

    @Nullable
    private static Method findMethodReflective(Class<?> target, String name, Class<?>... paramTypes) {
        Class<?> c = target;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /**
     * A convenient method to invoke a methodhandle.
     * @param mh the methodhandle to invoke
     * @param args the arguments to pass to the methodhandle
     * @return the result of the methodhandle invocation
     */
    private static Result<?> invoke(MethodHandle mh, Object... args) {
        try {
            return Result.success(mh.invoke(args));
        } catch (Throwable t) {
            LOGGER.error("Error while invoking method: {}({})", mh, Arrays.toString(args), t);
            return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
        }
    }

    private static Result<?> invoke(MethodHandle mh, List<Object> args) {
        try {
            return Result.success(mh.invokeWithArguments(args));
        } catch (Throwable t) {
            LOGGER.error("Error while invoking method: {}({})", mh, Arrays.toString(args.toArray()), t);
            return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
        }
    }

    public static Result<?> invokeWithParamType(Object caller, String name, List<Class<?>> paramTypes, Object... args){
        var handle = findMethodHandle(caller.getClass(), name, paramTypes.toArray(new Class[0]));
        if(handle != null){
            try {
                var qwq = new ArrayList<>(args.length + 1);
                qwq.add(caller);
                qwq.addAll(Arrays.asList(args));
                return Result.success(handle.invokeWithArguments(qwq));
            } catch (Throwable t) {
                LOGGER.error("Error while invoking method: {}({})", name, Arrays.toString(args), t);
                return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
            }
        }else{
            return Result.failure("Method not found: " + name, ExceptionCode.METHOD_NOT_FOUND);
        }
    }

    public static Result<?> invoke(Object caller, String name, Object... args){
        var handle = findMethodHandle(caller.getClass(), name, Arrays.stream(args).map(Object::getClass).toArray(Class[]::new));
        if(handle != null){
            try {
                var qwq = new ArrayList<>(args.length + 1);
                qwq.add(caller);
                qwq.addAll(Arrays.asList(args));
                return Result.success(handle.invokeWithArguments(qwq));
            } catch (Throwable t) {
                LOGGER.error("Error while invoking method: {}({})", name, Arrays.toString(args), t);
                return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
            }
        }else{
            return Result.failure("Method not found: " + name, ExceptionCode.METHOD_NOT_FOUND);
        }
    }

    public static Result<?> invoke(Object caller, String name){
        var handle = findMethodHandle(caller.getClass(), name);
        if(handle != null){
            try {
                return Result.success(handle.invoke(caller));
            } catch (Throwable t) {
                LOGGER.error("Error while invoking method: {}()", name, t);
                return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
            }
        }else{
            return Result.failure("Method not found: " + name, ExceptionCode.METHOD_NOT_FOUND);
        }
    }


    @SuppressWarnings("unchecked")
    public static <T> Result<T> methodAsFunctional(Class<T> funcInterface, Class<?> target, String methodName, Class<?>... paramTypes) {
        String k = "MH:LAMBDA:" + funcInterface.getName() + ":" + key(target, methodName, paramTypes);
        return Result.success((T) LAMBDA_CACHE.computeIfAbsent(k, kk -> {
            try {
                Method m = findMethodReflective(target, methodName, paramTypes);
                if (m == null) throw new NoSuchMethodException(methodName);
                m.setAccessible(true);
                MethodHandle mh = PUBLIC_LOOKUP.unreflect(m);
                MethodType invokedType = MethodType.methodType(funcInterface);
                MethodType sam = MethodType.methodType(mh.type().returnType(), mh.type().parameterArray());
                CallSite cs = LambdaMetafactory.metafactory(
                        PUBLIC_LOOKUP,
                        getSingleAbstractMethodName(funcInterface),
                        invokedType,
                        sam.erase(),   // erased form
                        mh,
                        mh.type()
                );
                return cs.getTarget().invoke();
            } catch (Throwable t) {
                return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
            }
        }));
    }

    private static String getSingleAbstractMethodName(Class<?> iface) {
        for (Method m : iface.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers())) return m.getName();
        }
        throw new IllegalArgumentException("Not a functional interface: " + iface);
    }

    //endregion

    @Nullable
    private static MethodHandle findConstructorHandle(Class<?> target, Class<?>... paramTypes) {
        String k = keyConstructor(target, paramTypes);
        return CONSTRUCTOR_HANDLE_CACHE.computeIfAbsent(k, kk -> {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(target, PUBLIC_LOOKUP);
                return lookup.findConstructor(target, MethodType.methodType(void.class, paramTypes));
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Failed to find constructor for class {} with params {}", target, Arrays.toString(paramTypes), e);
                return null;
            }
        });
    }

    public static <T> Result<T> newInstance(Class<T> target, Object... args) {
        Class<?>[] paramTypes = Arrays.stream(args).map(arg -> arg == null ? Object.class : arg.getClass()).toArray(Class<?>[]::new);
        MethodHandle ctor = findConstructorHandle(target, paramTypes);
        if (ctor == null) {
            return Result.failure("Constructor not found for " + target + " with params " + Arrays.toString(paramTypes), ExceptionCode.CONSTRUCTOR_NOT_FOUND);
        }
        try {
            return Result.success((T) ctor.invokeWithArguments(args));
        } catch (Throwable t) {
            LOGGER.error("Error while invoking constructor for class {} with args {}", target, Arrays.toString(args), t);
            return Result.failure(t.getMessage(), ExceptionCode.METHOD_INVOKE_EXCEPTION);
        }
    }
}
