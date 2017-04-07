package com.liuzhongjun.videorecorddemo.util;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by zz4760762 on 2015/7/27.
 */
public final class VideoUtils {
    public static void startTrim(File src, File dst, int startMs, int endMs) throws IOException {

        //ReadableByteChannel in = Channels.newChannel((new FileInputStream(src)));
        Movie movie = MovieCreator.build(src.getAbsolutePath());
        //RandomAccessFile randomAccessFile = new RandomAccessFile(src, "r");

        //DataSource ds = DataSource.
        //Movie movie = MovieCreator.build("D:\\Mp4\\test.mp4");//randomAccessFile.getChannel()

        // remove all tracks we will create new tracks from the old
        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());
//        for (Track track : tracks) {
//            printTime(track);
//        }

        double startTime = startMs/1000;
        double endTime = endMs/1000;

        boolean timeCorrected = false;

        // Here we try to find a track that has sync samples. Since we can only start decoding
        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
        // such a frame
        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                if (timeCorrected) {
                    // This exception here could be a false positive in case we have multiple tracks
                    // with sync samples at exactly the same positions. E.g. a single movie containing
                    // multiple qualities of the same video (Microsoft Smooth Streaming file)

                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                }
                startTime = correctTimeToSyncSample(track, startTime, false);//true
                endTime = correctTimeToSyncSample(track, endTime, true);//false
                timeCorrected = true;
            }
        }
        System.out.println("trim startTime-->"+startTime);
        System.out.println("trim endTime-->"+endTime);
        int x = 0;
        for (Track track : tracks) {
            long currentSample = 0;
            double currentTime = 0;
            long startSample = -1;
            long endSample = -1;
            x++;


            for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
                TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
                for (int j = 0; j < entry.getCount(); j++) {
                    // entry.getDelta() is the amount of time the current sample covers.

                    if (currentTime <= startTime) {
                        // current sample is still before the new starttime
                        startSample = currentSample;
                    }
                    if (currentTime <= endTime) {
                        // current sample is after the new start time and still before the new endtime
                        endSample = currentSample;
                    } else {
                        // current sample is after the end of the cropped video
                        break;
                    }
                    currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
                }
            }


            System.out.println("trim startSample-->"+startSample);
            System.out.println("trim endSample-->"+endSample);
            movie.addTrack(new CroppedTrack(track, startSample, endSample));
//            break;
        }


        //movie.addTrack(new CroppedTrack(track, startSample, endSample));

        //IsoFile out = (IsoFile) new DefaultMp4Builder().build(movie);
        Container container = new DefaultMp4Builder().build(movie);


        if (!dst.exists()) {
            dst.createNewFile();
        }

        FileOutputStream fos = new FileOutputStream(dst);
        FileChannel fc = fos.getChannel();
        //out.getBox(fc);  // This one build up the memory.
        container.writeContainer(fc);

        fc.close();
        fos.close();
        //randomAccessFile.close();
    }


    public static void appendVideo(Context context,String saveVideoPath,String[] videos) throws IOException{
        Movie[] inMovies = new Movie[videos.length];
        int index = 0;
        for(String video:videos)
        {
            inMovies[index] = MovieCreator.build(video);
            index++;
        }
        // 分别取出音轨和视频
        List<Track> videoTracks = new LinkedList<Track>();
        List<Track> audioTracks = new LinkedList<Track>();
        for (Movie m : inMovies) {
            for (Track t : m.getTracks()) {
                if (t.getHandler().equals("soun")) {
                    audioTracks.add(t);
                }
                if (t.getHandler().equals("vide")) {
                    videoTracks.add(t);
                }
            }
        }

        // 合并到最终的视频文件
        Movie result = new Movie();

        if (audioTracks.size() > 0) {
            result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
        }
        if (videoTracks.size() > 0) {
            result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
        }
        Container out = new DefaultMp4Builder().build(result);
        FileChannel fc = new RandomAccessFile(String.format(saveVideoPath), "rw").getChannel();
        out.writeContainer(fc);
        fc.close();
    }

//    protected static long getDuration(Track track) {
//        long duration = 0;
//        for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
//            duration += entry.getCount() * entry.getDelta();
//        }
//        return duration;
//    }

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }


    /**
     * 获取视频宽高（分辨率）
     * @param path
     * @return
     */
    public static String[] getVideoWidthHeight(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        String video_width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String video_height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String[] video_width_height = {video_width, video_height};
        return video_width_height;
    }
  /*  private static void printTime(Track track) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(),
                        currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero
                    // therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(
                            track.getSyncSamples(), currentSample + 1)] = currentTime;
                    System.out.println("currentTime-->" + currentTime);
                }
                currentTime += (double) entry.getDelta()
                        / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }

        // System.out.println("size-->"+currentSample);
		*//*
		 * for(int i=0;i<timeOfSyncSamples.length;i++){
		 * System.out.println("data-->"+timeOfSyncSamples[i]); }
		 *//*

		*//*
		 * double previous = 0; for (double timeOfSyncSample :
		 * timeOfSyncSamples) { if (timeOfSyncSample > cutHere) { if (next) {
		 * return timeOfSyncSample; } else { return previous; } } previous =
		 * timeOfSyncSample; } return timeOfSyncSamples[timeOfSyncSamples.length
		 * - 1];
		 *//*
    }*/
}
