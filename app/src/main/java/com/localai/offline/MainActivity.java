package com.localai.offline;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private LinearLayout root, content;
    private TextView modelStatus;
    private final List<ChatStore.Message> messages=new ArrayList<>();
    private TextView transcript, chatStatus, imageStatus;
    private Spinner textSpinner,imageSpinner,contextSpinner;
    private EditText systemPrompt,messageInput,tempInput,topPInput,topKInput,maxTokensInput;
    private static final int PICK_MODEL=900;
    private final BroadcastReceiver downloadReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String m=i.getStringExtra("message");if(modelStatus!=null)modelStatus.setText(m);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);AppPaths.root(this);messages.addAll(ChatStore.load(this));
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},77);
        buildShell();showChat();
        IntentFilter f=new IntentFilter(ModelDownloadService.ACTION_STATUS);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(downloadReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(downloadReceiver,f);
    }
    @Override public void onDestroy(){try{unregisterReceiver(downloadReceiver);}catch(Exception ignored){}super.onDestroy();}

    private void buildShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(8),dp(8),dp(8),dp(8));
        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        addTab(tabs,R.string.tab_chat,this::showChat);addTab(tabs,R.string.tab_image,this::showImage);addTab(tabs,R.string.tab_models,this::showModels);addTab(tabs,R.string.tab_settings,this::showSettings);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(52)));content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void addTab(LinearLayout p,int label,Runnable r){Button b=button(getString(label));b.setOnClickListener(v->r.run());p.addView(b,new LinearLayout.LayoutParams(0,-1,1));}
    private void reset(){content.removeAllViews();}

    private void showChat(){
        reset(); addTitle(R.string.tab_chat);
        textSpinner=new Spinner(this);refreshSpinner(textSpinner,AppPaths.listTextModels(this),R.string.no_text_model);addLabeled(R.string.chat_model,textSpinner);
        systemPrompt=edit(getPreferences(0).getString("system",getString(R.string.default_system_prompt)),3);addLabeled(R.string.system_prompt,systemPrompt);
        transcript=new TextView(this);transcript.setTextIsSelectable(true);transcript.setTextSize(16);transcript.setPadding(dp(10),dp(10),dp(10),dp(10));renderTranscript();content.addView(transcript,new LinearLayout.LayoutParams(-1,-2));
        messageInput=edit("",3);messageInput.setHint(R.string.message_hint);content.addView(messageInput,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout a=new LinearLayout(this);a.setOrientation(LinearLayout.HORIZONTAL);tempInput=num("0.7");topPInput=num("0.8");topKInput=num("20");maxTokensInput=num("768");
        addMini(a,R.string.temperature,tempInput);addMini(a,R.string.top_p,topPInput);addMini(a,R.string.top_k,topKInput);addMini(a,R.string.max_tokens,maxTokensInput);content.addView(a);
        contextSpinner=new Spinner(this);contextSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"4096","8192"}));content.addView(label(R.string.context));content.addView(contextSpinner);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);Button send=button(getString(R.string.send));Button stop=button(getString(R.string.stop));Button clear=button(getString(R.string.clear));
        send.setOnClickListener(v->sendMessage());stop.setOnClickListener(v->LlamaServer.stop());clear.setOnClickListener(v->{messages.clear();ChatStore.clear(this);renderTranscript();});buttons.addView(send,new LinearLayout.LayoutParams(0,-2,1));buttons.addView(stop,new LinearLayout.LayoutParams(0,-2,1));buttons.addView(clear,new LinearLayout.LayoutParams(0,-2,1));content.addView(buttons);
        chatStatus=new TextView(this);content.addView(chatStatus);
    }

    private void sendMessage(){
        File model=selectedFile(textSpinner,AppPaths.listTextModels(this));if(model==null){toast(R.string.no_text_model);return;}if(!LlamaServer.engineExists(this)){toast(R.string.engine_missing);return;}
        String prompt=messageInput.getText().toString().trim();if(prompt.isEmpty())return;
        String system=systemPrompt.getText().toString();getPreferences(0).edit().putString("system",system).apply();
        double temp=d(tempInput,0.7),topP=d(topPInput,0.8);int topK=n(topKInput,20),max=n(maxTokensInput,768),ctx=Integer.parseInt((String)contextSpinner.getSelectedItem());
        List<ChatStore.Message> before=new ArrayList<>(messages);messages.add(new ChatStore.Message("user",prompt));messageInput.setText("");renderTranscript();StringBuilder answer=new StringBuilder();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LlamaServer.chat(this,model,system,before,prompt,temp,topP,topK,max,ctx,new LlamaServer.Callback(){
            public void onStatus(String s){runOnUiThread(()->chatStatus.setText(s));}
            public void onToken(String s){answer.append(s);runOnUiThread(()->renderStreaming(answer.toString()));}
            public void onDone(){messages.add(new ChatStore.Message("assistant",answer.toString()));ChatStore.save(MainActivity.this,messages);runOnUiThread(()->{renderTranscript();chatStatus.setText(R.string.engine_ready);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}
            public void onError(String e){runOnUiThread(()->{chatStatus.setText(getString(R.string.error_prefix)+e);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}
        });
    }
    private void renderTranscript(){if(transcript==null)return;StringBuilder b=new StringBuilder();for(ChatStore.Message m:messages)b.append(m.role.equals("user")?getString(R.string.user_label):getString(R.string.assistant_label)).append(":\n").append(m.content).append("\n\n");transcript.setText(b.toString());}
    private void renderStreaming(String s){renderTranscript();transcript.append(getString(R.string.assistant_label)+":\n"+s);}

    private void showImage(){
        reset();addTitle(R.string.tab_image);imageSpinner=new Spinner(this);refreshSpinner(imageSpinner,AppPaths.listImageModels(this),R.string.no_image_model);addLabeled(R.string.image_model,imageSpinner);
        EditText p=edit("",3);addLabeled(R.string.prompt,p);EditText neg=edit("",2);addLabeled(R.string.negative_prompt,neg);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);EditText w=num("512"),h=num("512"),steps=num("20"),cfg=num("7.0"),seed=num("-1");addMini(row,R.string.width,w);addMini(row,R.string.height,h);addMini(row,R.string.steps,steps);addMini(row,R.string.cfg,cfg);content.addView(row);addLabeled(R.string.seed,seed);
        LinearLayout imageButtons=new LinearLayout(this);imageButtons.setOrientation(LinearLayout.HORIZONTAL);Button gen=button(getString(R.string.generate));Button stopImage=button(getString(R.string.stop));stopImage.setOnClickListener(v->ImageEngine.stop());imageButtons.addView(gen,new LinearLayout.LayoutParams(0,-2,1));imageButtons.addView(stopImage,new LinearLayout.LayoutParams(0,-2,1));content.addView(imageButtons);imageStatus=new TextView(this);imageStatus.setTextIsSelectable(true);content.addView(imageStatus);
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);content.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        content.addView(label(R.string.recent_images));
        HorizontalScrollView galleryScroll=new HorizontalScrollView(this);LinearLayout gallery=new LinearLayout(this);gallery.setOrientation(LinearLayout.HORIZONTAL);galleryScroll.addView(gallery);content.addView(galleryScroll,new LinearLayout.LayoutParams(-1,dp(190)));renderGallery(gallery,preview);
        gen.setOnClickListener(v->{File m=selectedFile(imageSpinner,AppPaths.listImageModels(this));if(m==null){toast(R.string.no_image_model);return;}if(!ImageEngine.engineExists(this)){toast(R.string.engine_missing);return;}String prompt=p.getText().toString().trim();if(prompt.isEmpty())return;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);ImageEngine.generate(this,m,prompt,neg.getText().toString(),n(w,512),n(h,512),n(steps,20),d(cfg,7.0),lng(seed,-1),new ImageEngine.Callback(){public void onStatus(String s){runOnUiThread(()->imageStatus.setText(s));}public void onDone(File f){runOnUiThread(()->{imageStatus.setText(getString(R.string.image_ready)+"\n"+f.getAbsolutePath());preview.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));renderGallery(gallery,preview);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}public void onError(String e){runOnUiThread(()->{imageStatus.setText(getString(R.string.error_prefix)+e);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}});});
    }

    private void showModels(){
        reset();addTitle(R.string.models_title);modelStatus=new TextView(this);modelStatus.setText(statusText());content.addView(modelStatus);
        content.addView(labelText(getString(R.string.recommended_desc)));Button rec=button(getString(R.string.recommended_pack));rec.setOnClickListener(v->startDownloads("text4b,image"));content.addView(rec);
        content.addView(labelText(getString(R.string.quality_desc)));Button q=button(getString(R.string.quality_model));q.setOnClickListener(v->startDownloads("text8b"));content.addView(q);
        Button imp=button(getString(R.string.import_model));imp.setOnClickListener(v->pickModel());content.addView(imp);Button ref=button(getString(R.string.refresh));ref.setOnClickListener(v->modelStatus.setText(statusText()));content.addView(ref);content.addView(labelText(getString(R.string.downloads_wifi_note)));
    }
    private String statusText(){StringBuilder b=new StringBuilder();for(ModelCatalog.Item x:new ModelCatalog.Item[]{ModelCatalog.TEXT_4B,ModelCatalog.TEXT_8B,ModelCatalog.IMAGE})b.append(x.name).append(": ").append(x.destination(this).isFile()?getString(R.string.installed):getString(R.string.not_installed)).append('\n');b.append("\nllama-server: ").append(LlamaServer.engineExists(this)?getString(R.string.installed):getString(R.string.not_installed));b.append("\nsd-cli: ").append(ImageEngine.engineExists(this)?getString(R.string.installed):getString(R.string.not_installed));return b.toString();}
    private void startDownloads(String ids){
        long need=0;for(String id:ids.split(",")){ModelCatalog.Item x=ModelCatalog.get(id.trim());if(x!=null&&!x.destination(this).isFile())need+=x.expectedBytes;}
        long free=new StatFs(AppPaths.root(this).getAbsolutePath()).getAvailableBytes();
        if(free < need + 1_073_741_824L){toast(R.string.not_enough_space);return;}
        Intent i=new Intent(this,ModelDownloadService.class).putExtra(ModelDownloadService.EXTRA_IDS,ids);startForegroundService(i);toast(R.string.download_started);
    }
    private void pickModel(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/octet-stream","application/x-gguf","*/*"});startActivityForResult(i,PICK_MODEL);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_MODEL&&result==RESULT_OK&&data!=null&&data.getData()!=null)importModel(data.getData());}
    private void importModel(Uri uri){Executors.newSingleThreadExecutor().execute(()->{try{String name=queryName(uri);String lower=name.toLowerCase();File dir=lower.endsWith(".gguf")?AppPaths.textModels(this):AppPaths.imageModels(this);File dst=new File(dir,name);try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new BufferedOutputStream(new FileOutputStream(dst))){if(in==null)throw new IOException("Cannot open file");in.transferTo(out);}runOnUiThread(()->{toast(R.string.import_done);if(modelStatus!=null)modelStatus.setText(statusText());});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,getString(R.string.import_failed)+" "+e.getMessage(),Toast.LENGTH_LONG).show());}});}
    private String queryName(Uri u){String last=u.getLastPathSegment();if(last==null||last.isBlank())return "model_"+System.currentTimeMillis()+".gguf";int slash=last.lastIndexOf('/');return slash>=0?last.substring(slash+1):last;}

    private void renderGallery(LinearLayout gallery,ImageView preview){
        gallery.removeAllViews();File[] fs=AppPaths.outputs(this).listFiles((d,n)->n.toLowerCase().endsWith(".png"));if(fs==null)return;Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        int limit=Math.min(10,fs.length);for(int i=0;i<limit;i++){File f=fs[i];ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));v.setPadding(dp(4),dp(4),dp(4),dp(4));v.setOnClickListener(x->preview.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath())));gallery.addView(v,new LinearLayout.LayoutParams(dp(180),dp(180)));}
    }

    private void showSettings(){
        reset();addTitle(R.string.settings_title);content.addView(label(R.string.device_profile));content.addView(label(R.string.uncensored_text));content.addView(label(R.string.privacy_text));content.addView(label(R.string.files_location));content.addView(label(R.string.language));
        LinearLayout langs=new LinearLayout(this);Button uk=button(getString(R.string.ukrainian)),en=button(getString(R.string.english));uk.setOnClickListener(v->setLang("uk"));en.setOnClickListener(v->setLang("en"));langs.addView(uk,new LinearLayout.LayoutParams(0,-2,1));langs.addView(en,new LinearLayout.LayoutParams(0,-2,1));content.addView(langs);
        Button stop=button(getString(R.string.stop_engine));stop.setOnClickListener(v->{LlamaServer.stop();toast(R.string.engine_off);});content.addView(stop);
    }
    private void setLang(String tag){LocaleManager lm=getSystemService(LocaleManager.class);lm.setApplicationLocales(LocaleList.forLanguageTags(tag));}

    private void refreshSpinner(Spinner s,List<File> files,int empty){List<String> names=new ArrayList<>();if(files.isEmpty())names.add(getString(empty));else for(File f:files)names.add(f.getName());s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));}
    private File selectedFile(Spinner s,List<File> files){if(s==null||files.isEmpty())return null;int i=s.getSelectedItemPosition();return i>=0&&i<files.size()?files.get(i):files.get(0);}
    private void addTitle(int id){TextView t=label(id);t.setTextSize(24);t.setPadding(0,dp(8),0,dp(12));content.addView(t);}
    private TextView label(int id){return labelText(getString(id));}
    private TextView labelText(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(15);t.setPadding(dp(4),dp(8),dp(4),dp(6));return t;}
    private void addLabeled(int id,View v){content.addView(label(id));content.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void addMini(LinearLayout row,int id,EditText e){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(label(id));box.addView(e);row.addView(box,new LinearLayout.LayoutParams(0,-2,1));}
    private EditText edit(String text,int lines){EditText e=new EditText(this);e.setText(text);e.setMinLines(lines);e.setGravity(Gravity.TOP|Gravity.START);e.setTextSize(16);return e;}
    private EditText num(String x){EditText e=new EditText(this);e.setText(x);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(int id){Toast.makeText(this,id,Toast.LENGTH_SHORT).show();}
    private static int n(EditText e,int d){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return d;}}
    private static long lng(EditText e,long d){try{return Long.parseLong(e.getText().toString().trim());}catch(Exception x){return d;}}
    private static double d(EditText e,double d){try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception x){return d;}}
}
