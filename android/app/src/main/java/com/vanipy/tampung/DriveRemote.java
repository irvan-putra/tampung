package com.vanipy.tampung;

import com.vanipy.tampung.core.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Own-app scope only. No update, trash, delete or automatic retry operation exists. */
final class DriveRemote implements BackupEngine.Remote {
    static final long BUDGET_BYTES = 1024 * 1024;
    static final String SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    private static final String API = "https://www.googleapis.com/drive/v3/files";
    private static final String MARKER = "tampung-synthetic-v1";
    private final String token;
    DriveRemote(String token) { this.token = token; }
    static final class Entry {
        final String id, name; final long size;
        Entry(String id, String name, long size) { this.id=id; this.name=name; this.size=size; }
    }
    List<Entry> list() throws Exception {
        List<Entry> result = new ArrayList<>(); String page = ""; Set<String> seenPages = new HashSet<>();
        do {
            String query="spaces=appDataFolder&pageSize=100&fields="+enc("nextPageToken,files(id,name,size,appProperties)");
            if (!page.isEmpty()) query += "&pageToken="+enc(page);
            JSONObject response = new JSONObject(new String(request(API+"?"+query,"GET",null,null,512*1024), StandardCharsets.UTF_8));
            JSONArray files=response.optJSONArray("files");
            if (files!=null) for(int i=0;i<files.length();i++) {
                JSONObject f=files.getJSONObject(i); JSONObject props=f.optJSONObject("appProperties");
                if(props!=null && MARKER.equals(props.optString("kind"))) result.add(new Entry(f.getString("id"),f.optString("name","snapshot"),Long.parseLong(f.getString("size"))));
            }
            page=response.optString("nextPageToken", "");
            if(!page.isEmpty() && !seenPages.add(page)) throw new IOException("Repeated history page. No upload attempted.");
            if (result.size()>10000 || seenPages.size()>200) throw new IOException("History too large for this experiment. No upload attempted.");
        } while(!page.isEmpty());
        result.sort((a,b)->b.name.compareTo(a.name));
        return result;
    }
    public String createSnapshot(byte[] bytes) throws Exception {
        SnapshotCodec.decode(bytes);
        long retained=0; for(Entry e:list()) { if(e.size<0) throw new IOException("Invalid history size"); retained=Math.addExact(retained,e.size); }
        if(retained+bytes.length>BUDGET_BYTES) throw new IOException("1 MiB experiment budget reached. History was not deleted.");
        String id=UUID.randomUUID().toString();
        JSONObject meta=new JSONObject(); meta.put("name","tampung-"+System.currentTimeMillis()+"-"+id+".bin");
        meta.put("parents",new JSONArray().put("appDataFolder"));
        meta.put("appProperties",new JSONObject().put("kind",MARKER).put("operationId",id));
        String boundary="tampung_"+UUID.randomUUID().toString().replace("-","");
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+meta.toString()+"\r\n--"+boundary+"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes); body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] response=request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id","POST",body.toByteArray(),"multipart/related; boundary="+boundary,65536);
        return new JSONObject(new String(response,StandardCharsets.UTF_8)).getString("id");
    }
    public byte[] download(String id) throws Exception {
        // Resolve ID only against current account's marked app-specific history.
        boolean found=false; for(Entry e:list()) if(e.id.equals(id)) { found=true; break; }
        if(!found) throw new IOException("Snapshot not found in this account's tampung history");
        return request(API+"/"+enc(id)+"?alt=media","GET",null,null,SnapshotCodec.MAX_BYTES);
    }
    private byte[] request(String address,String method,byte[] body,String contentType,int limit) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestMethod(method); connection.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null) { connection.setDoOutput(true); connection.setRequestProperty("Content-Type",contentType); connection.setFixedLengthStreamingMode(body.length); }
        try {
            if(body!=null)try(OutputStream out=connection.getOutputStream()){out.write(body);}
            int code=connection.getResponseCode();
            if(code<200||code>=300) {
                if(code==401||code==403)throw new IOException("Google denied access ("+code+"). Nothing local was replaced. Retry authorization manually.");
                if(code==429)throw new IOException("Google rate limit. Stopped; no automatic retry.");
                throw new IOException("Google request failed ("+code+"). An upload may have arrived; check history before retrying.");
            }
            try(InputStream in=connection.getInputStream()){return LocalStore.readBounded(in,limit);}
        } finally {connection.disconnect();}
    }
    private static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
}
