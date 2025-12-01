package com.akliakhandler.lightspeedsubmission;

import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public final class DeepClone {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize Unsafe", e);
        }
    }

    private static final Set<Class<?>> TERMINAL_TYPES = new HashSet<>(Arrays.asList(
            String.class,
            Integer.class, Long.class, Double.class, Float.class,
            Short.class, Byte.class, Boolean.class, Character.class
    ));


    public static <T> T deepClone(T root) {
        if (root == null) return null;

        try {
            IdentityHashMap<Object, Object> visited = new IdentityHashMap<>();
            ArrayDeque<Object> queue = new ArrayDeque<>();

            Object rootClone = shallowClone(root, visited, queue);

            while (!queue.isEmpty()) {
                Object original = queue.poll();
                Object clone = visited.get(original);
                if (clone == null) continue;

                Class<?> clazz = original.getClass();

                if (clazz.isArray()) {
                    int length = Array.getLength(original);
                    for (int i = 0; i < length; i++) {
                        Object value = Array.get(original, i);
                        Object valueClone = shallowClone(value, visited, queue);
                        Array.set(clone, i, valueClone);
                    }
                    continue;
                }

                if (original instanceof Map<?, ?> map) {
                    Map<Object, Object> cloneMap = (Map<Object, Object>) clone;
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        Object keyClone = shallowClone(e.getKey(), visited, queue);
                        Object valueClone = shallowClone(e.getValue(), visited, queue);
                        cloneMap.put(keyClone, valueClone);
                    }
                    continue;
                }

                if (original instanceof Collection<?> coll) {
                    Collection<Object> cloneColl = (Collection<Object>) clone;
                    for (Object item : coll) {
                        Object itemClone = shallowClone(item, visited, queue);
                        cloneColl.add(itemClone);
                    }
                    continue;
                }

                if (isJdkPackage(clazz)) {
                    continue;
                }

                Class<?> current = clazz;
                while (current != null && current != Object.class) {
                    Field[] fields = current.getDeclaredFields();
                    for (Field field : fields) {
                        int mods = field.getModifiers();

                        if (Modifier.isStatic(mods)) continue;
                        if (Modifier.isTransient(mods)) continue;
                        if (field.isSynthetic()) continue;

                        if (!field.canAccess(original)) {
                            field.setAccessible(true);
                        }

                        Object fieldValue = field.get(original);
                        Object clonedValue = shallowClone(fieldValue, visited, queue);
                        field.set(clone, clonedValue);
                    }
                    current = current.getSuperclass();
                }
            }

            T result = (T) rootClone;
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Deep clone failed", e);
        }
    }

    private static Object shallowClone(Object value,
                                       IdentityHashMap<Object, Object> visited,
                                       ArrayDeque<Object> queue) throws Exception {
        if (value == null) return null;

        if (visited.containsKey(value)) {
            return visited.get(value);
        }

        Class<?> clazz = value.getClass();

        if (isTerminalType(clazz)) {
            return value;
        }

        if (clazz.isArray()) {
            int length = Array.getLength(value);
            Object newArray = Array.newInstance(clazz.getComponentType(), length);
            visited.put(value, newArray);
            queue.add(value);
            return newArray;
        }

        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> newMap = createMapInstance(clazz, map);
            visited.put(value, newMap);
            queue.add(value);
            return newMap;
        }

        if (value instanceof Collection<?> coll) {
            Collection<Object> newColl = createCollectionInstance(clazz, coll);
            visited.put(value, newColl);
            queue.add(value);
            return newColl;
        }

        if (isJdkPackage(clazz)) {
            return value;
        }

        Object newObject = createObjectInstance(clazz);
        visited.put(value, newObject);
        queue.add(value);
        return newObject;
    }

    private static boolean isTerminalType(Class<?> clazz) {
        if (clazz.isPrimitive()) return true;
        if (TERMINAL_TYPES.contains(clazz)) return true;
        return Enum.class.isAssignableFrom(clazz);
    }

    private static boolean isJdkPackage(Class<?> clazz) {
        Package p = clazz.getPackage();
        String name = p != null ? p.getName() : "";
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.");
    }

    private static Map<Object, Object> createMapInstance(Class<?> clazz, Map<?, ?> original) throws Exception {
        if (original == null) return null;

        Constructor<?> noArg = getPublicNoArg(clazz);
        if (noArg != null) {
            Object inst = noArg.newInstance();
            if (inst instanceof Map) {
                return (Map<Object, Object>) inst;
            }
        }

        if (isJdkContainerClass(clazz)) {

            if (original instanceof EnumMap<?, ?> em) {
                EnumMap<?, ?> copy = new EnumMap<>(em);
                copy.clear();
                return (Map<Object, Object>) copy;
            }

            if (original instanceof SortedMap<?, ?> sm) {
                return (Map<Object, Object>) new TreeMap<>(sm.comparator());
            }

            return new HashMap<>();
        }

        Object unsafeInst = UNSAFE.allocateInstance(clazz);
        if (unsafeInst instanceof Map) {
            return (Map<Object, Object>) unsafeInst;
        }

        return new LinkedHashMap<>();
    }

    private static Collection<Object> createCollectionInstance(Class<?> clazz, Collection<?> original) throws Exception {
        if (original == null) return null;

        Constructor<?> noArg = getPublicNoArg(clazz);
        if (noArg != null) {
            Object inst = noArg.newInstance();
            if (inst instanceof Collection) {
                return (Collection<Object>) inst;
            }
        }

        if (isJdkContainerClass(clazz)) {

            if (original instanceof EnumSet<?> es) {
                EnumSet<?> copy = EnumSet.copyOf(es);
                copy.clear();
                return (Collection<Object>) copy;
            }

            if (original instanceof SortedSet<?> ss) {
                return (Collection<Object>) new TreeSet<>(ss.comparator());
            }

            if (original instanceof List<?>) {
                return new ArrayList<>();
            }
            if (original instanceof Set<?>) {
                return new HashSet<>();
            }
            if (original instanceof Queue<?>) {
                return new ArrayDeque<>();
            }

            return new ArrayList<>();
        }

        Object unsafeInst = UNSAFE.allocateInstance(clazz);
        if (unsafeInst instanceof Collection) {
            return (Collection<Object>) unsafeInst;
        }

        return new ArrayList<>();
    }

    private static Constructor<?> getPublicNoArg(Class<?> c) {
        try {
            Constructor<?> ctor = c.getConstructor();
            if (Modifier.isPublic(ctor.getModifiers())) {
                ctor.setAccessible(true);
                return ctor;
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    private static boolean isJdkContainerClass(Class<?> c) {
        if (c == null) return false;
        if (isJdkPackage(c)) return true;
        Class<?> s = c.getSuperclass();
        while (s != null && s != Object.class) {
            if (isJdkPackage(s)) return true;
            s = s.getSuperclass();
        }
        return false;
    }

    private static Object createObjectInstance(Class<?> clazz) throws Exception {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            if (!ctor.canAccess(null)) {
                ctor.setAccessible(true);
            }
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            return UNSAFE.allocateInstance(clazz);
        }
    }
}