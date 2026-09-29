package com.localai.offline;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AppPaths {
    private AppPaths() {}
    public static File root(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File f = new File(base, "LocalAI"); f.mkdirs(); return f;
    }
    public static File textModels(Context c) { File f = new File(root(c), "models/text"); f.mkdirs(); return f; }
    public static File imageModels(Context c) { File f = new File(root(c), "models/image"); f.mkdirs(); return f; }
    public static File outputs(Context c) { File f = new File(root(c), "outputs"); f.mkdirs(); return f; }
    public static List<File> listTextModels(Context c) { return list(textModels(c), new String[]{".gguf"}); }
    public static List<File> listImageModels(Context c) { return list(imageModels(c), new String[]{".safetensors", ".ckpt", ".gguf"}); }
    private static List<File> list(File dir, String[] exts) {
        File[] files = dir.listFiles(); List<File> out = new ArrayList<>(); if (files == null) return out;
        for (File f : files) { if (!f.isFile()) continue; String n = f.getName().toLowerCase(); for (String ext : exts) if (n.endsWith(ext)) { out.add(f); break; } }
        out.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)); return out;
    }
}
