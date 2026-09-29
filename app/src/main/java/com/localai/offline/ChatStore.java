package com.localai.offline;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class ChatStore {
    public static final class Message { public final String role, content; public Message(String role,String content){this.role=role;this.content=content;} }
    private static final String PREF="chat"; private static final String KEY="messages"; private ChatStore(){}
    public static List<Message> load(Context c){
        List<Message> out=new ArrayList<>();
        try{ JSONArray a=new JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]")); for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new Message(o.optString("role"),o.optString("content")));} }catch(Exception ignored){}
        return out;
    }
    public static void save(Context c,List<Message> m){ JSONArray a=new JSONArray(); try{for(Message x:m){JSONObject o=new JSONObject();o.put("role",x.role);o.put("content",x.content);a.put(o);}}catch(Exception ignored){} c.getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply(); }
    public static void clear(Context c){c.getSharedPreferences(PREF,0).edit().remove(KEY).apply();}
}
