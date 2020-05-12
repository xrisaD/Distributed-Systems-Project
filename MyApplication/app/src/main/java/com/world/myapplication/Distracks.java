package com.world.myapplication;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;


public class Distracks extends Application {
    private Consumer consumer;
    ArrayList<MediaPlayer> players;
    int currentMediaPlayer = 0;
    ArrayList<MediaPlayer> onlinePlayers = new ArrayList<>();
    MediaPlayer offlinePlayer = new MediaPlayer();
    String currentDataSource;
    private  AsyncDownload runner;

    public String lastSearch = "";
    public ArrayList<MusicFileMetaData> lastSearchResult= null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        consumer = new Consumer();
        consumer.addBroker(new Component("192.168.1.13", 5000));

        consumer.setPath(getFilesDir());
        //this.readBroker(getFilesDir().getAbsolutePath()+"brokers.txt");

    }

    public void streamSongOffline(String path){
        currentlyStreamingOnline = false;
        Log.e("pathhhhhaki ", path);
        try{
            resetEverything();
            offlinePlayer = new MediaPlayer();
            offlinePlayer.setDataSource(path);
            offlinePlayer.prepare();
            offlinePlayer.start();
        }
        catch (IOException e){

        }
    }
    int mediaPlayerIndex;
    public void pauseOnlineStreaming(){
        for(int i = 0 ; i < onlinePlayers.size() ; i++){
            if(onlinePlayers.get(i).isPlaying()){
                onlinePlayers.get(i).pause();
                mediaPlayerIndex = i;
            }
        }
    }
    public void resumeOnlineStreaming(){
        onlinePlayers.get(mediaPlayerIndex).start();
    }
    public void pauseOfflineStreaming(){
        this.offlinePlayer.pause();
    }
    public void resumeOfflineStreaming(){
        offlinePlayer.start();
    }
    boolean currentlyStreamingOnline  = true;
    public void pause(){
        if(currentlyStreamingOnline){
            pauseOnlineStreaming();
        }
        else{
            pauseOfflineStreaming();
        }

    }
    public void resume(){
        if(currentlyStreamingOnline){
            resumeOnlineStreaming();
        }
        else {
            pauseOfflineStreaming();
        }
    }
    public void resetEverything(){
        try{
            System.out.println("RESETTING EVERYTHING");
            if(onlinePlayers != null) {
                for (MediaPlayer mp : onlinePlayers) {
                    mp.reset();
                    mp.release();
                }
            }
            onlinePlayers = null;
            if(offlinePlayer != null) {
                offlinePlayer.reset();
                offlinePlayer.release();
                offlinePlayer = null;
            }
            System.out.println("SUCCESSFULLY RESETED EVERYTHING");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    public void streamSongOnline(String artistName, String songName){
        resetEverything();
        currentlyStreamingOnline = true;
        StreamSong streamSong = new StreamSong();
        MusicFileMetaData metaData= new MusicFileMetaData(songName , artistName , null , null , null,0);
        streamSong.execute(metaData);

    }


    private void forceChangeMediaPlayer(MediaPlayer mp , String newDataSource){
        System.out.println("force changed caled");
        try {
            mp.reset();
            mp.setDataSource(newDataSource);
            currentDataSource = newDataSource;
            mp.prepare();
            mp.start();
        } catch (IOException e) {
            forceChangeMediaPlayer(mp , newDataSource);
        }
    }

    // Async for streaming
    private class StreamSong extends AsyncTask<MusicFileMetaData , Void , Void>{
        MusicFileMetaData musicFileMetaData;
        @Override
        protected Void doInBackground(MusicFileMetaData... artistAndSong) {

            musicFileMetaData = artistAndSong[0];
            //get data
            ArtistName artist = new ArtistName(musicFileMetaData.getArtistName());
            String songName = musicFileMetaData.getTrackName();

            Component b = consumer.getBroker(artist);
            String ip = b.getIp();
            int port = b.getPort();

            Socket s = null;
            ObjectInputStream in = null;
            ObjectOutputStream out = null;
            try {
                //While we find a broker who is not responsible for the artistname
                Request.ReplyFromBroker reply=null;
                int statusCode = Request.StatusCodes.NOT_RESPONSIBLE;
                while(statusCode == Request.StatusCodes.NOT_RESPONSIBLE){
                    s = new Socket(ip, port);
                    //Creating the request to Broker for this artist
                    out = new ObjectOutputStream(s.getOutputStream());
                    consumer.requestPullToBroker(artist, songName, out);
                    //Waiting for the reply


                    in = new ObjectInputStream(s.getInputStream());
                    reply = (Request.ReplyFromBroker) in.readObject();
                    System.out.printf("[CONSUMER] Got reply from Broker(%s,%d) : %s%n", ip, port, reply);
                    statusCode = reply.statusCode;
                    ip = reply.responsibleBrokerIp;
                    port = reply.responsibleBrokerPort;
                }
                if(statusCode == Request.StatusCodes.NOT_FOUND){
                    System.out.println("Song or Artist does not exist");
                    throw new Exception("Song or Artist does not exist");
                }
                //Song exists and the broker is responsible for the artist
                else if(statusCode == Request.StatusCodes.OK){
                    //Save the information that this broker is responsible for the requested artist
                    consumer.register(new Component(s.getInetAddress().getHostAddress(),s.getPort()) , artist);
                    //download mp3 to the device
                    stream(reply.numChunks, in , out, songName);
                }
                //In this case the status code is MALFORMED_REQUEST
                else{
                    System.out.println("MALFORMED_REQUEST");
                    throw new Exception("MALFORMED_REQUEST");
                }
            }
            catch(ClassNotFoundException e){
                //Protocol Error (Unexpected Object Caught) its a protocol error
                System.out.printf("[CONSUMER] Unexpected object on playData %s " , e.getMessage());
                e.printStackTrace();
            }
            catch (Exception e){
                System.out.printf("[CONSUMER] Error on playData %s " , e.getMessage());
                e.printStackTrace();
            }
            finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (s != null) s.close();
                }
                catch(Exception e){
                    System.out.printf("[CONSUMER] Error while closing socket on playData %s " , e.getMessage());
                    e.printStackTrace();
                }

            }
            return null;
        }
        private void stream(final int numChunks, ObjectInputStream in, ObjectOutputStream out, String filename) throws IOException, ClassNotFoundException {
            int size = 0;
            //Start reading chunks
            onlinePlayers = new ArrayList<>();
            //Creating as many medialayer objects as there are chunks
            for(int i = 0 ; i < numChunks ; i++){
                MediaPlayer temp = new MediaPlayer();
                onlinePlayers.add(temp);
            }
            for (int i = 0; i < numChunks; i++) {
                //ask for the next chunk
                Request.RequestToBroker requestToBroker= new Request.RequestToBroker();
                requestToBroker.method = Request.Methods.NEXT_CHUNK;
                out.writeObject(requestToBroker);
                out.flush();

                //HandleCHunks
                Object object = in.readObject();
                    if (object instanceof MusicFile) {

                        MusicFile chunk = (MusicFile) object;

                        System.out.println("[CONSUMER] got chunk Number " + i);
                        System.out.println();

                        size += chunk.getMusicFileExtract().length;
                        //Add chunk to the icomplete list
                        String tempFilename = getFilesDir() + "/temp" + i + ".mp3";
                        try (FileOutputStream fos = new FileOutputStream(tempFilename)) {
                            fos.write(chunk.getMusicFileExtract());
                        }
                        catch (IOException e){
                            e.printStackTrace();
                        }
                        MediaPlayer chunkPlaya = onlinePlayers.get(i);
                        chunkPlaya.setDataSource(tempFilename);
                        chunkPlaya.prepare();
                        //If the current media player is not the first we chain it with the previous one
                        if(i-1 >= 0) {
                            System.out.println("Setting next media player @ " + i + "currFilename " + tempFilename);
                            onlinePlayers.get(i-1).setNextMediaPlayer(chunkPlaya);
                        }
                        if(i ==0){
                            chunkPlaya.start();
                        }
                    }
                }

            Request.RequestToBroker requestToBroker= new Request.RequestToBroker();
            requestToBroker.method = Request.Methods.THE_END;
            out.writeObject(requestToBroker);
            out.flush();
        }
    }

    private void saveChunk(MusicFile chunk , String filename){
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            System.out.println("Saving a chunk to filename " + filename);
            fos.write(chunk.getMusicFileExtract());
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    //Async for download
    public class AsyncDownload extends AsyncTask<MusicFileMetaData, Integer, String> {
        private ArrayList<MusicFile> chunks = new ArrayList<>();
        int PROGRESS_MAX;
        int PROGRESS_CURRENT;
        int notificationId;
        NotificationManagerCompat notificationManager;
        NotificationCompat.Builder builder;
        MusicFileMetaData musicFileMetaData = null;

        @Override
        protected String doInBackground(MusicFileMetaData... artistAndSong) {

            musicFileMetaData = artistAndSong[0];
            //get data
            ArtistName artist = new ArtistName(musicFileMetaData.getArtistName());
            String songName = musicFileMetaData.getTrackName();

            Component b = consumer.getBroker(artist);
            String ip = b.getIp();
            int port = b.getPort();

            Socket s = null;
            ObjectInputStream in = null;
            ObjectOutputStream out = null;
            try {
                //While we find a broker who is not responsible for the artistname
                Request.ReplyFromBroker reply=null;
                int statusCode = Request.StatusCodes.NOT_RESPONSIBLE;
                while(statusCode == Request.StatusCodes.NOT_RESPONSIBLE){
                    s = new Socket(ip, port);
                    //Creating the request to Broker for this artist
                    out = new ObjectOutputStream(s.getOutputStream());
                    consumer.requestPullToBroker(artist, songName, out);
                    //Waiting for the reply


                    in = new ObjectInputStream(s.getInputStream());
                    reply = (Request.ReplyFromBroker) in.readObject();
                    System.out.printf("[CONSUMER] Got reply from Broker(%s,%d) : %s%n", ip, port, reply);
                    statusCode = reply.statusCode;
                    ip = reply.responsibleBrokerIp;
                    port = reply.responsibleBrokerPort;
                }
                if(statusCode == Request.StatusCodes.NOT_FOUND){
                    System.out.println("Song or Artist does not exist");
                    throw new Exception("Song or Artist does not exist");
                }
                //Song exists and the broker is responsible for the artist
                else if(statusCode == Request.StatusCodes.OK){
                    //Save the information that this broker is responsible for the requested artist
                    consumer.register(new Component(s.getInetAddress().getHostAddress(),s.getPort()) , artist);
                    //download mp3 to the device
                    download(reply.numChunks, in , out, songName);
                }
                //In this case the status code is MALFORMED_REQUEST
                else{
                    System.out.println("MALFORMED_REQUEST");
                    throw new Exception("MALFORMED_REQUEST");
                }
            }
            catch(ClassNotFoundException e){
                //Protocol Error (Unexpected Object Caught) its a protocol error
                System.out.printf("[CONSUMER] Unexpected object on playData %s " , e.getMessage());
                e.printStackTrace();
            }
            catch (Exception e){
                System.out.printf("[CONSUMER] Error on playData %s " , e.getMessage());
                e.printStackTrace();
            }
            finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (s != null) s.close();
                }
                catch(Exception e){
                    System.out.printf("[CONSUMER] Error while closing socket on playData %s " , e.getMessage());
                    e.printStackTrace();
                }

            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            notificationManager = NotificationManagerCompat.from(getApplicationContext());
            builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_audiotrack_light)
                    .setContentTitle("Download Song ")
                    .setContentText("Download in progress...")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            PROGRESS_MAX = 100;
            PROGRESS_CURRENT = 0;
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            notificationId = 1;
            // notificationId is a unique int for each notification that you must define
            notificationManager.notify(1, builder.build());

        }
        boolean completed = false;
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            PROGRESS_CURRENT = 100;
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            if(completed) {
                builder.setContentText("Download complete")
                        .setProgress(0, 0, false);
                notificationManager.notify(notificationId, builder.build());
            }else{
                builder.setContentText("Can't download")
                        .setProgress(0, 0, false);
                notificationManager.notify(notificationId, builder.build());
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if(values[0]>0) {
                PROGRESS_CURRENT = PROGRESS_CURRENT + values[0];
                builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            }
            notificationManager.notify(notificationId, builder.build());
        }

        //Download song and save with given filename
        private void download(int numChunks, ObjectInputStream in, ObjectOutputStream out, String filename) throws IOException, ClassNotFoundException {
            //Initializing donwload incomplete list
            chunks = new ArrayList<>(numChunks);
            int size = 0;
            //Start reading chunks

            Integer progressStep = new Integer(PROGRESS_MAX/(numChunks+1));
            for (int i = 0; i < numChunks; i++) {
                //ask for the next chunk
                Request.RequestToBroker requestToBroker= new Request.RequestToBroker();
                requestToBroker.method = Request.Methods.NEXT_CHUNK;
                out.writeObject(requestToBroker);
                out.flush();

                //HandleCHunks
                Object object = in.readObject();
                if(object instanceof MusicFile) {

                    MusicFile chunk = (MusicFile) object;

                    System.out.println("[CONSUMER] got chunk Number " + i);
                    System.out.println();

                    size += chunk.getMusicFileExtract().length;
                    //Add chunk to the icomplete list
                    chunks.add(chunk);
                    publishProgress(progressStep);
                }

            }
            Request.RequestToBroker requestToBroker= new Request.RequestToBroker();
            requestToBroker.method = Request.Methods.THE_END;
            out.writeObject(requestToBroker);
            out.flush();

            if(chunks.size() == numChunks) {
                save(chunks, filename + ".mp3");
            }
        }

        // Save a list of music files as entire mp3 with the given filename
        private void save(ArrayList<MusicFile> chunks , String filename) throws IOException {
            System.out.println("Saving a song to " + getFilesDir().getAbsolutePath()+ "/" + filename);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();//baos stream gia bytes
            for(MusicFile chunk : chunks){
                baos.write(chunk.getMusicFileExtract());
            }
            byte[] concatenated_byte_array = baos.toByteArray();//metatrepei to stream se array
            try (FileOutputStream fos = new FileOutputStream(getFilesDir() + "/" + filename)) {
                fos.write(concatenated_byte_array);
            }
            completed = true;
        }
    }

    public Consumer getConsumer() {
        return consumer;
    }

    private static final String CHANNEL_ID = "BasicChannel";
    private void createNotificationChannel() {
        String channel_name = "Basic chanel";
        String channel_description = "Download";
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = channel_name;
            String description = channel_description;
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public  void download(MusicFileMetaData artistAndSong){
        runner = new AsyncDownload();
        runner.execute(artistAndSong);
    }
}