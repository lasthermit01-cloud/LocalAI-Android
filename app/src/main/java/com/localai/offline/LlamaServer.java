package com.localai.offline;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LlamaServer {
    public interface Callback { void onStatus(String s); void onToken(String s); void onDone(); void onError(String e); }
    private static final ExecutorService EXEC=Executors.newCachedThreadPool();
    private static final Object LOCK=new Object();
    private static Process server;
    private static String serverModel="";
    private static int serverContext=0;
    private static final int PORT=8189;
    private static volatile HttpURLConnection activeRequest;

    private LlamaServer(){}

    public static boolean engineExists(Context c){return engine(c).isFile();}
    private static File engine(Context c){return new File(c.getApplicationInfo().nativeLibraryDir,"libllama_server.so");}

    public static void stop(){
        synchronized(LOCK){if(server!=null){server.destroy();server=null;}serverModel="";serverContext=0;}
        HttpURLConnection r=activeRequest; if(r!=null)r.disconnect();
    }

    public static void chat(Context c, File model, String system, List<ChatStore.Message> history,
                            String prompt, double temp,double topP,int topK,int maxTokens,int ctx,Callback cb){
        EXEC.execute(()->{
            try{
                ensure(c,model,ctx,cb);
                JSONObject req=new JSONObject();
                req.put("model","local"); req.put("stream",true); req.put("temperature",temp); req.put("top_p",topP);
                req.put("top_k",topK); req.put("max_tokens",maxTokens);
                JSONArray msgs=new JSONArray();
                if(system!=null&&!system.isBlank()){JSONObject o=new JSONObject();o.put("role","system");o.put("content",system);msgs.put(o);}
                for(ChatStore.Message m:history){JSONObject o=new JSONObject();o.put("role",m.role);o.put("content",m.content);msgs.put(o);}
                JSONObject u=new JSONObject();u.put("role","user");u.put("content",prompt);msgs.put(u); req.put("messages",msgs);
                URL url=new URL("http://localhost:"+PORT+"/v1/chat/completions");
                HttpURLConnection con=(HttpURLConnection)url.openConnection(); activeRequest=con;
                con.setRequestMethod("POST");con.setDoOutput(true);con.setConnectTimeout(5000);con.setReadTimeout(0);
                con.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=con.getOutputStream()){out.write(req.toString().getBytes(StandardCharsets.UTF_8));}
                if(con.getResponseCode()!=200)throw new IOException("llama-server HTTP "+con.getResponseCode()+": "+readError(con));
                try(BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8))){
                    String line;
                    while((line=br.readLine())!=null){
                        if(!line.startsWith("data:"))continue;
                        String data=line.substring(5).trim(); if(data.equals("[DONE]"))break;
                        try{
                            JSONObject o=new JSONObject(data); JSONObject delta=o.getJSONArray("choices").getJSONObject(0).optJSONObject("delta");
                            if(delta!=null){String token=delta.optString("content",""); if(!token.isEmpty())cb.onToken(token);}
                        }catch(Exception ignored){}
                    }
                } finally {activeRequest=null;con.disconnect();}
                cb.onDone();
            }catch(Throwable t){activeRequest=null;cb.onError(t.getMessage()==null?t.toString():t.getMessage());}
        });
    }

    private static void ensure(Context c,File model,int ctx,Callback cb)throws Exception{
        synchronized(LOCK){
            if(server!=null&&server.isAlive()&&model.getAbsolutePath().equals(serverModel)&&ctx==serverContext&&healthy())return;
            if(server!=null)server.destroy();
            File e=engine(c); if(!e.isFile())throw new FileNotFoundException(c.getString(R.string.engine_missing));
            cb.onStatus(c.getString(R.string.loading_model));
            List<String> cmd=new ArrayList<>();
            cmd.add(e.getAbsolutePath()); cmd.add("-m");cmd.add(model.getAbsolutePath());
            cmd.add("--host");cmd.add("127.0.0.1");cmd.add("--port");cmd.add(String.valueOf(PORT));
            cmd.add("-c");cmd.add(String.valueOf(ctx));cmd.add("-t");cmd.add("6");cmd.add("-tb");cmd.add("8");
            cmd.add("--jinja");cmd.add("-ngl");cmd.add("0");cmd.add("--log-disable");
            ProcessBuilder pb=new ProcessBuilder(cmd);pb.redirectErrorStream(true);
            pb.environment().put("LD_LIBRARY_PATH",c.getApplicationInfo().nativeLibraryDir);
            server=pb.start();serverModel=model.getAbsolutePath();serverContext=ctx;
            Process s=server;
            EXEC.execute(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()))){while(r.readLine()!=null){}}catch(Exception ignored){}});
        }
        long until=System.currentTimeMillis()+120_000;
        while(System.currentTimeMillis()<until){
            if(server==null||!server.isAlive())throw new IOException("llama-server exited while loading model");
            if(healthy())return;
            Thread.sleep(500);
        }
        throw new IOException("Timed out loading model");
    }

    private static boolean healthy(){
        try{HttpURLConnection c=(HttpURLConnection)new URL("http://localhost:"+PORT+"/health").openConnection();c.setConnectTimeout(300);c.setReadTimeout(300);int code=c.getResponseCode();c.disconnect();return code>=200&&code<500;}catch(Exception e){return false;}
    }
    private static String readError(HttpURLConnection c){try(InputStream in=c.getErrorStream()){if(in==null)return "";return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
}
