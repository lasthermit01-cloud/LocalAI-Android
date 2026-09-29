package com.localai.offline;

import android.content.Context;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ModelCatalog {
    public enum Kind { TEXT, IMAGE }
    public static final class Item {
        public final String id, name, fileName, url, sha256;
        public final Kind kind;
        public final long expectedBytes;
        Item(String id, String name, String fileName, String url, String sha256, Kind kind, long expectedBytes) {
            this.id=id; this.name=name; this.fileName=fileName; this.url=url; this.sha256=sha256; this.kind=kind; this.expectedBytes=expectedBytes;
        }
        public File destination(Context c) {
            File dir = kind == Kind.TEXT ? AppPaths.textModels(c) : AppPaths.imageModels(c);
            return new File(dir, fileName);
        }
    }

    public static final Item TEXT_4B = new Item(
        "text4b",
        "Qwen3 4B 2507 Uncensored Aggressive Q4_K_M",
        "Qwen3-4B-2507-Instruct-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
        "https://huggingface.co/HauhauCS/Qwen3-4B-2507-Instruct-Uncensored-HauhauCS-Aggressive/resolve/main/Qwen3-4B-2507-Instruct-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf?download=true",
        "6615b7b5184931e4df9c6d0ae9cd29ca9319b73908d4423283d4cc401a12a1cd",
        Kind.TEXT, 2_500_000_000L);

    public static final Item TEXT_8B = new Item(
        "text8b",
        "Qwen3 8B Abliterated Q4_K_M",
        "qwen3-8b-abliterated-Q4_K_M.gguf",
        "https://huggingface.co/richardyoung/Qwen3-8B-Abliterated-GGUF/resolve/main/qwen3-8b-abliterated-Q4_K_M.gguf?download=true",
        "8625e48da4c4be9bcba2414fd8cad4095ff3a538d5b0111c2b26b5f6209538b9",
        Kind.TEXT, 5_030_000_000L);

    public static final Item IMAGE = new Item(
        "image",
        "DreamShaper 8 (SD 1.5)",
        "DreamShaper_8_pruned.safetensors",
        "https://huggingface.co/Lykon/DreamShaper/resolve/main/DreamShaper_8_pruned.safetensors?download=true",
        "879db523c30d3b9017143d56705015e15a2cb5628762c11d086fed9538abd7fd",
        Kind.IMAGE, 2_130_000_000L);

    private static final Map<String, Item> MAP = new HashMap<>();
    static { MAP.put(TEXT_4B.id,TEXT_4B); MAP.put(TEXT_8B.id,TEXT_8B); MAP.put(IMAGE.id,IMAGE); }
    public static Item get(String id) { return MAP.get(id); }
    private ModelCatalog() {}
}
