import javafx.application.Application;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaMarkerEvent;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Path;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javafx.util.Duration;
import org.apache.commons.io.IOUtils ;

public class MusicPlayer extends Application {

    private IncompleteList<MusicFile> list;
    MediaPlayer mediaplayer;
    IncompleteList<MediaPlayer> mediaPlayers;
    public MusicPlayer(){

    }
    public MusicPlayer(IncompleteList<MusicFile> list){
        this.list = list;
        launch("test");
    }

    public static void main(String[] args){
        launch(args);
    }
    //FKIN GLBOAL TO SOLVE MY PROBLEMS
    private boolean MARKER_HANDLER_EXECUTED = false;

    public void start(Stage stage) {
        //Circle circ = new Circle(40, 40, 30);
        //Group root = new Group(circ);
        // scene = new Scene(root, 400, 300);


        //play(new MusicFile(songs.get(1) , songData));
        //stage.setTitle("My JavaFX Application");
       // stage.setScene(scene);
        stage.show();


        System.out.println("Number of active threads from the given thread: " + Thread.activeCount());

        System.out.println("Shiet\n");
        System.out.println("Getting song metadata");
        List<MusicFileMetaData> songs = new ArrayList<>();
        songs.add(new MusicFileMetaData());
        songs.get(0).setPath("untitled.mp3");
        MusicFile mf = readFully(songs.get(0));
        //Play two songs at the same time
        List<MusicFile> chunks = breakMusicFile(mf , 50000);
        //System.out.println(chunks);

        IncompleteList<MusicFile> nlist = new IncompleteList<>(chunks.size());
        for(MusicFile m : chunks){
            nlist.add(m);
        }
        System.out.println("chunks : " + chunks.size());
        this.list = nlist;
        this.play();

    }
    //METHODS
    public void play(){
        _playSequentially(list);
    }

    /**
     *  Outputs every musicFile as they become available (they come over the network) as a temp mp3 file
     *  and outputs the filenames as medias for use by another thread
     */
    private void _outputData(IncompleteList<MusicFile> musicFiles , IncompleteList<MediaPlayer> filenames){
        int i = 0;
        for(MusicFile mf : musicFiles){
            String filename = "tmp/chunk" + i + ".mp3";
            createTempFile(filename);
            musicFileToMp3(mf , filename);
            Media m = new Media(new File(filename).toURI().toString());



            MediaPlayer mp = new MediaPlayer(m);
            mp.setOnReady(new Runnable() {
                @Override
                public void run() {
                    Media mpMedia = mp.getMedia();
                    ObservableMap<String, Duration> markers = mpMedia.getMarkers();
                    //markers.put("START", Duration.ZERO);
                    //markers.put("INTERVAL",mpMedia.getDuration().divide(2.0));
                    markers.put("SWITCH", mpMedia.getDuration().subtract(new Duration(30)));

                    System.out.println(markers);
                }
            });
            filenames.add(mp);
            i++;
        }
    }

    /**
     * Starts playing medias as soon as they become available
     */
    private void _playMediaTracks(Iterator<MediaPlayer> it) {
        if (!it.hasNext()) {
            System.err.println("Iterator doesn't have next");
            return;
        }
        MediaPlayer tmp = it.next();
       // mediaplayer.currentTimeProperty().get().toMillis();
        //mediaplayer.stopTimeProperty().get().toMillis();



        //mediaplayer.setCycleCount(5);
        //mediaplayer.set
        tmp.setOnEndOfMedia(new Runnable() {
            @Override
            public void run() {
                //_playMediaTracks(it);
                if(!MARKER_HANDLER_EXECUTED) {
                    System.out.println("End of media handler executed");
                    _playMediaTracks(it);
                }
                else{
                    //marker handler was executed
                    MARKER_HANDLER_EXECUTED = false;
                }
            }
        });
        tmp.setOnMarker(new EventHandler<MediaMarkerEvent>() {
            @Override
            public void handle(MediaMarkerEvent event) {
                MARKER_HANDLER_EXECUTED = true;
                System.out.println("Marker handler executed");
                _playMediaTracks(it);
            }
        });
        tmp.play();
    }

    private void _playSequentially(IncompleteList<MusicFile> musicFiles){
        //One thread will createThF
        mediaPlayers =  new IncompleteList<>(musicFiles.numItems);
        new Thread(()-> _outputData(musicFiles,mediaPlayers)).start();
        new Thread(()-> _playMediaTracks(mediaPlayers.iterator())).start();
    }


    // IO HELPER FUNCTIONS


    public static List<MusicFile> breakMusicFile(MusicFile musicFile, int chunkSize){
        byte[] largeFile = musicFile.getMusicFileExtract();
        ArrayList<byte[]> extracts = new ArrayList<>();
        int offset = 0;
        while(offset < largeFile.length){

            byte[] chunk = new byte[chunkSize];
            //If this is the last chunk and the bytes left are less than the chunk's size
            if(largeFile.length - offset < chunkSize){
                chunk = new byte[largeFile.length - offset];
            }
            for(int i = 0 ; i < chunk.length ; i++){
                chunk[i] = largeFile[offset];
                offset++;
            }
            extracts.add(chunk);
            if(offset >= largeFile.length){
                break;
            }
        }
        ArrayList<MusicFile> mfs = new ArrayList<>();
        for(byte[] b : extracts){
            mfs.add(new MusicFile(musicFile.getMetaData() , b));
        }
        return mfs;
    }
    public static MusicFile readFully(MusicFileMetaData md){
        byte[] res = null;
        try (FileInputStream fos = new FileInputStream(md.getPath())) {
            res = IOUtils.toByteArray(fos);
        }
        catch(Exception e){
            System.err.println(e.getMessage());
        }
        return new MusicFile(md,res);
    }
    public void play(MusicFile file , String tempFileName, Runnable next) {
        System.out.println(file);
        createTempFile(tempFileName);

        musicFileToMp3(file , tempFileName);

        String bip = tempFileName;
        Media hit = new Media(new File(bip).toURI().toString());
        MediaPlayer mediaPlayer = new MediaPlayer(hit);
        mediaPlayer.play();

    }
    public static void musicFileToMp3(MusicFile file, String filename) {
        try (FileOutputStream fos = new FileOutputStream(filename , false)) {
            fos.write(file.getMusicFileExtract());
        }
        catch(Exception e){
            System.err.println(e.getMessage());
        }
    }
    public static void createTempFile(String fileName){
        new File("tmp").mkdir();
        File tmpFile = new File(fileName);

        try{
            tmpFile.delete();
            tmpFile.createNewFile();
            tmpFile.deleteOnExit();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}