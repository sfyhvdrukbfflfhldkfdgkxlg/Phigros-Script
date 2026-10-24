package io.github.phiscript;
public final class RuntimeState {public static final RuntimeState INSTANCE=new RuntimeState();public int errors;public void log(String s){errors++;}}
