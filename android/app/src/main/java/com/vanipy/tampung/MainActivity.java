package com.vanipy.tampung;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.vanipy.tampung.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public final class MainActivity extends Activity {
    private static final int AUTH_REQUEST=71;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LocalStore local;
    private TextView state, notes;
    private LinearLayout content;
    private final List<Button> controls=new ArrayList<>();
    private final Map<String,byte[]> simulatedSnapshots=new LinkedHashMap<>();
    private String pendingAction;
    private boolean busy;
    private interface Work { String run() throws Exception; }
    private final BackupEngine.Remote fake=new BackupEngine.Remote(){
        public String createSnapshot(byte[] bytes){String id=UUID.randomUUID().toString();simulatedSnapshots.put(id,bytes.clone());return id;}
        public byte[] download(String id)throws Exception{byte[] b=simulatedSnapshots.get(id);if(b==null)throw new IOException("Local simulation snapshot not found");return b.clone();}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved); local=new LocalStore(getFilesDir());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,244,238));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(20),dp(24),dp(28));scroll.addView(content);
        setContentView(scroll);
        if(Build.VERSION.SDK_INT>=35)scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        label("tampung",32,true);label("0.1.0 · synthetic backup experiment",14,false);
        label("Keep recovery separate from sync",22,true);
        label("Dummy notes only. No real files, vaults or messages. A checksum detects corruption; it is not encryption.",16,false);
        label("LOCAL SIMULATION",13,true);
        button("Generate dummy notes",()->run(()->{local.replaceAtomically(LocalStore.fixtures());return "Dummy notes generated locally. No Google request.";}));
        button("Create local test snapshot",()->run(()->{new BackupEngine(fake,local).backup();return "LOCAL simulation verified. "+simulatedSnapshots.size()+" snapshots in memory, lost when the app closes. Not a Google backup.";}));
        button("Clear local dummy notes",()->new AlertDialog.Builder(this).setTitle("Clear only these dummy notes?").setMessage("This does not erase any cloud or simulated snapshots. Clear only after creating a test snapshot if you want to test restore.").setNegativeButton("Cancel",null).setPositiveButton("Clear dummy notes",(d,w)->run(()->{local.replaceAtomically(Collections.emptyList());return "Local dummy notes cleared. Snapshots were not deleted.";})).show());
        button("Restore local test snapshot",()->{if(simulatedSnapshots.isEmpty()){status("Create a local test snapshot first. No cloud backup exists from this action.");return;}String last=new ArrayList<>(simulatedSnapshots.keySet()).get(simulatedSnapshots.size()-1);confirmRestore(()->{new BackupEngine(fake,local).restore(last);return "LOCAL simulation restored. No Google request.";});});
        label("GOOGLE DRIVE",13,true);
        label(BuildConfig.DRIVE_CONFIGURED ? "Manual app-private snapshots. Google account approval required." : "Setup incomplete. Google buttons are disabled until the developer registers this app and install certificate. No cloud test has run.",16,false);
        Button backup=button("Back up dummy notes to Drive",()->authorize("backup"));backup.setEnabled(BuildConfig.DRIVE_CONFIGURED);backup.setTag("cloud");
        Button history=button("Choose a Drive snapshot",()->authorize("history"));history.setEnabled(BuildConfig.DRIVE_CONFIGURED);history.setTag("cloud");
        label("HISTORY & STORAGE",13,true);
        label("Default: append new snapshots; retain all history. No automatic overwrite, prune or delete. Cloud test budget: 1 MiB. Pause when full, never remove recovery copies.",16,false);
        label("Protection limit: this app avoids deletion, but an attacker with its Google access or your account can still delete history. Drive app-private storage is not immutable.",16,false);
        label("Plan price: not checked. No paid plan or billing enabled. Full snapshot estimate: snapshot bytes × backups/day × days retained. Google storage and network charges depend on your plan and connection.",15,false);
        state=label("Ready for a local dummy-data test. Google round-trip: not verified.",16,true);notes=label("",15,false);refresh();
    }
    private void authorize(String action){
        if(!BuildConfig.DRIVE_CONFIGURED){status("Google setup incomplete. No authorization attempted.");return;}
        if(busy)return;setBusy(true);pendingAction=action;
        AuthorizationRequest req=AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope(DriveRemote.SCOPE))).build();
        Identity.getAuthorizationClient(this).authorize(req).addOnSuccessListener(result->{
            if(result.hasResolution())try{startIntentSenderForResult(result.getPendingIntent().getIntentSender(),AUTH_REQUEST,null,0,0,0);}
            catch(Exception e){finishError();}
            else useToken(result);
        }).addOnFailureListener(e->finishError());
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=AUTH_REQUEST)return;
        if(result!=RESULT_OK){setBusy(false);status("Google authorization cancelled. Nothing local was replaced.");return;}
        try{useToken(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));}catch(Exception e){finishError();}
    }
    private void useToken(AuthorizationResult authorization){
        String token=authorization.getAccessToken();String action=pendingAction;pendingAction=null;
        if(token==null||!authorization.getGrantedScopes().contains(DriveRemote.SCOPE)){finishError();return;}
        DriveRemote remote=new DriveRemote(token);
        setBusy(false);
        if("backup".equals(action))run(()->{new BackupEngine(remote,local).backup();return "GOOGLE snapshot uploaded and byte-verified. Earlier snapshots retained. Clear and restore is a separate test.";});
        else {
            setBusy(true);worker.execute(()->{try{
                List<DriveRemote.Entry> entries=remote.list();long bytes=0;for(DriveRemote.Entry e:entries)bytes+=e.size;final long used=bytes;
                runOnUiThread(()->{setBusy(false);status("Google history: "+entries.size()+" snapshots, "+used+" bytes retained. Budget 1048576 bytes. Plan price unknown. Listed snapshots are not all restore-verified.");
                    if(entries.isEmpty())return;
                    String[] names=new String[entries.size()];for(int i=0;i<names.length;i++)names[i]=entries.get(i).name+" ("+entries.get(i).size+" bytes)";
                    new AlertDialog.Builder(this).setTitle("Choose snapshot in approved account").setItems(names,(d,index)->confirmRestore(()->{new BackupEngine(remote,local).restore(entries.get(index).id);return "GOOGLE snapshot restored into this app's dummy notes. Check all fields below.";})).setNegativeButton("Cancel",null).show();});
            }catch(Exception e){runOnUiThread(()->{setBusy(false);status(safe(e));});}});
        }
    }
    private void confirmRestore(Work work){new AlertDialog.Builder(this).setTitle("Replace local dummy notes?").setMessage("Only this app's dummy notes will be replaced after the snapshot passes validation. No cloud history will be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Restore dummy notes",(d,w)->run(work)).show();}
    private void run(Work work){if(busy)return;setBusy(true);worker.execute(()->{String message;try{message=work.run();}catch(Exception e){message=safe(e);}final String done=message;runOnUiThread(()->{setBusy(false);status(done);refresh();});});}
    private String safe(Exception e){if(e instanceof IOException)return e.getMessage();return "Operation failed. Nothing was automatically deleted. Check setup and try manually.";}
    private void finishError(){setBusy(false);status("Google authorization failed. Check developer setup or cancel. No local data was replaced.");}
    private void refresh(){try{StringBuilder b=new StringBuilder("LOCAL DUMMY NOTES\n");List<SnapshotCodec.Note> list=local.read();for(SnapshotCodec.Note n:list)b.append(n.id).append(": ").append(n.text).append("\n");if(list.isEmpty())b.append("Empty");notes.setText(b.toString());}catch(Exception e){notes.setText("Local snapshot could not be read; nothing overwritten.");}}
    private void setBusy(boolean value){busy=value;for(Button b:controls)b.setEnabled(!value&&(!"cloud".equals(b.getTag())||BuildConfig.DRIVE_CONFIGURED));}
    private void status(String message){state.setText(message);}
    private TextView label(String text,int size,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Color.rgb(25,47,42));if(bold)v.setTypeface(null,1);v.setPadding(0,dp(10),0,dp(8));content.addView(v);return v;}
    private Button button(String text,Runnable click){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setMinHeight(dp(52));content.addView(b,new LinearLayout.LayoutParams(-1,-2));controls.add(b);b.setOnClickListener(v->click.run());return b;}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();}
}
