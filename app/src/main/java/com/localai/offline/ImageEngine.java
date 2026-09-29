package com.localai.offline;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageEngine {
    public interface Callback { void onStatus(String s); void onDone(File f); void onError(String e); }
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static volatile Process active;
    private ImageEngine(){}
    private static File engine(Context c){return new File(c.getApplicationInfo().nativeLibraryDir,"libsd_cli.so");}
    public static boolean engineExists(Context c){return engine(c).isFile();}
    public static void stop(){Process p=active;if(p!=null)p.destroy();}
    public static void generate(Context c,File model,String prompt,String negative,int w,int h,int steps,double cfg,long seed,Callback cb){
        EXEC.execute(()->{
            try{
                File e=engine(c);if(!e.isFile())throw new FileNotFoundException(c.getString(R.string.engine_missing));
                File out=new File(AppPaths.outputs(c),"image_"+System.currentTimeMillis()+".png");
                List<String> cmd=new ArrayList<>();cmd.add(e.getAbsolutePath());cmd.add("-m");cmd.add(model.getAbsolutePath());cmd.add("-p");cmd.add(prompt);
                if(negative!=null&&!negative.isBlank()){cmd.add("-n");cmd.add(negative);} cmd.add("-o");cmd.add(out.getAbsolutePath());cmd.add("-W");cmd.add(String.valueOf(w));cmd.add("-H");cmd.add(String.valueOf(h));cmd.add("--steps");cmd.add(String.valueOf(steps));cmd.add("--cfg-scale");cmd.add(String.valueOf(cfg));cmd.add("--seed");cmd.add(String.valueOf(seed));cmd.add("--sampling-method");cmd.add("euler_a");
                ProcessBuilder pb=new ProcessBuilder(cmd);pb.redirectErrorStream(true);pb.environment().put("LD_LIBRARY_PATH",c.getApplicationInfo().nativeLibraryDir);
                active=pb.start();StringBuilder log=new StringBuilder();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(active.getInputStream()))){String line;while((line=r.readLine())!=null){log.append(line).append('\n');cb.onStatus(line);}}
                int code=active.waitFor();active=null;if(code!=0||!out.isFile())throw new IOException("sd-cli exit "+code+"\n"+tail(log.toString(),1200));cb.onDone(out);
            }catch(Throwable t){active=null;cb.onError(t.getMessage()==null?t.toString():t.getMessage());}
        });
    }
    private static String tail(String s,int n){return s.length()<=n?s:s.substring(s.length()-n);}
}
