package io.github.phiscript.capture;

/** Package IDs from the upstream Android manifests; do not accept arbitrary forks. */
public final class PhiraPackages {
    public static final String OFFICIAL="org.flos.phira",PRO="org.flos.phirapro";
    private PhiraPackages(){}
    public static boolean contains(String packageName){return OFFICIAL.equals(packageName)||PRO.equals(packageName);}
    public static String[] all(){return new String[]{OFFICIAL,PRO};}
}
