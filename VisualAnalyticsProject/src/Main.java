import com.oracle.tools.packager.IOUtils;
import jdk.management.cmm.SystemResourcePressureMXBean;
import org.json.JSONException;

import mdsj.MDSJ;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;

class User {
    static ArrayList<User> users = new ArrayList<>();

    String name;
    String topTag;
    ArrayList<Artist> artists;

    User(String name, String topTag) {
        this.name = name;
        this.topTag = topTag;
        artists = new ArrayList();
    }

    void addArtist(int j, String name, String play) {
        artists.add(new Artist(name, Integer.parseInt(play)));
//        artists[j] = new Artist(name, Integer.parseInt(play));
    }
}

class Artist {
    String name;
    int playcount;

    Artist(String name, int playCount) {
        this.name = name;
        this.playcount = playCount;
    }
}

public class Main {

    /**
     * JSONArray string from API:
     * users:
     * [{
     *      name: string,
     *      artists:
     *      [{
     *           name: string,
     *           playcount: int
     *      }]
     * }]
     */
    static int NUM_USERS;
    static int NUM_ARTISTS = 50;

    public static void main(String[] args) throws JSONException, IOException, ParseException {

        loadJSON("jsonArrayInputTopTag1000.json");
        double[][] dissimilarities = getDissimilarityMatrix();
        double[][] output = MDSJ.classicalScaling(dissimilarities);
        writeToFile(output);
    }

    /**
     * Transform points to [0,1]
     * @param input points
     * @return transformed points
     */
    static double[][] transformPoints(double[][] input) {
        int n = input[0].length;

        double[][] output = new double[2][n];

        double minX = 50, minY = 50, maxX = -50, maxY = -50;
        for (int i = 0; i < n; i++) {
            double x = input[0][i], y = input[1][i];

            if (x < minX) { minX = x; }
            if (y < minY) { minY = y; }
            if (x > maxX) { maxX = x; }
            if (y > maxY) { maxY = y; }
        }

        for (int i = 0; i < n; i++) {
            output[0][i] = (input[0][i] - minX)/(maxX - minX);
            output[1][i] = (input[1][i] - minY)/(maxY - minY);
        }

        return output;
    }

    /**
     * Write output to CSV file
     * @param output x,y array
     * @throws IOException
     */
    static void writeToFile(double[][] output) throws IOException {
        StringBuilder b = new StringBuilder();
//        b.append("x,y,name,topGenre");
//        for (int i = 1; i <= 50; i++) {
//            b.append(",A" + i + ",P" + i);
//        }
//        b.append("\n");
//
//        for (int i = 0; i < output[0].length; i++) {
//            User u = User.users.get(i);
//
//            b.append(output[0][i] + "," + output[1][i] + "," + u.name + "," + u.topTag);
//
//            for (int j = 0; j < u.artists.size(); j++) {
//                Artist a = u.artists.get(j);
//                b.append("," + a.name + "," + a.playcount);
//            }
//
//            b.append("\n");
//        }

        b.append("[" + "\n");
        for (int i = 0; i < output[0].length; i++) {
            User u = User.users.get(i);

            b.append("{" + "\n");
            b.append("\"x\": " + output[0][i] + ',' + "\n");
            b.append("\"y\": " + output[1][i] + "," + "\n");
            b.append("\"name\": \"" + u.name + "\"," + "\n");
            b.append("\"topTag\": \"" + u.topTag + "\"," + "\n");
            b.append("\"artists\":  [" + "\n");

            for (int j = 0; j < u.artists.size(); j++) {
                Artist a = u.artists.get(j);
                b.append("{" + "\n");
                String n = a.name;
                n.replaceAll("\\\\", "\\\\\\\\");
                n.replace("\"", "'");
                b.append("\"name\": \"" + n + "\"," + "\n");
                b.append("\"playcount\": " + a.playcount + "\n");
                b.append("}" + "\n");
                if (j != u.artists.size() - 1) {
                    b.append("," + "\n");
                }
            }
            b.append("]" + "\n");
            b.append("}" + "\n");

            if (i != output[0].length - 1) {
                b.append(",");
            }
        }

        b.append("]");

        BufferedWriter w = new BufferedWriter(new FileWriter("CosineMDSPointsTopTagsArtists1000.json"));
        w.write(b.toString());
        w.close();
    }

    /**
     * Calculate similarity matrix for users
     * @return NxN matrix of user similarities
     */
    static double[][] getDissimilarityMatrix() {
        double[][] dissimilarities = new double[NUM_USERS][NUM_USERS];

        for (int i = 0; i < NUM_USERS; i++) {
            ArrayList<Artist> userA = User.users.get(i).artists;
            for (int j = 0; j < NUM_USERS; j++) {
                ArrayList<Artist> userB = User.users.get(j).artists;
                //System.out.println(i + ": " + User.users.get(i).name + " " + j + ": " + User.users.get(i).name);
                dissimilarities[i][j] = 1 - getSimilarity(userA, userB);
            }
        }

        DecimalFormat df = new DecimalFormat("#.##");
        for (int i = 0; i < NUM_USERS; i++) {
            for (int j = 0; j < NUM_USERS; j++) {
                System.out.print(df.format(dissimilarities[i][j]));
                System.out.print("\t");
            }
            System.out.println();
        }

        return dissimilarities;
    }

    /**
     * Convert JSON string data into structured objects
     * @throws JSONException
     */
    static void loadJSON(String fileName) throws JSONException, IOException, ParseException {
        JSONParser parser = new JSONParser();
        JSONArray jUsers = (JSONArray) parser.parse(new FileReader(fileName));

        int i = 0;
        for (Object u : jUsers) {
            JSONObject jUser = (JSONObject) u;
            JSONArray jArtists = (JSONArray) jUser.get("artists");

            // Only allow users with a minimum number of artists for comparison
            if (jArtists.size() == NUM_ARTISTS) {
                User user = new User((String) jUser.get("name"), (String) jUser.get("topGenre"));
                User.users.add(i, user);
                int j = 0;
                for (Object a : jArtists) {
                    JSONObject jArtist = (JSONObject) a;
                    user.addArtist(j, (String) jArtist.get("name"), (String) jArtist.get("playcount"));
                    j++;
                }
                i++;
            } else {
                System.out.println(jArtists.size());
            }
        }
        NUM_USERS = i;
    }

    /**
     * Get the similarity of two users
     * @param userA top artists and playcounts
     * @param userB top artists and playcounts
     * @return similarity value
     */
    static double getSimilarity(ArrayList<Artist> userA, ArrayList<Artist> userB) {
        // Check how many combined unique artists
        ArrayList<String> uniqueArtists = new ArrayList<>();
        for(Artist a : userA) {
            uniqueArtists.add(a.name);
        }
        for (Artist a : userB) {
            if(!uniqueArtists.contains(a.name)) {
                uniqueArtists.add(a.name);
            }
        }

        int n = uniqueArtists.size();

        int[] u1 = new int[n];
        int[] u2 = new int[n];

        for (int i = 0; i < n; i++) {
            String artist = uniqueArtists.get(i);

            u1[i] = getPlaycount(userA, artist);
            u2[i] = getPlaycount(userB, artist);
        }

//        for (int i = 0; i < n; i++) {
//            System.out.println(uniqueArtists.get(i) + ": " + u1[i] + " : " + u2[i]);
//        }

        return cosineSimilarity(u1, u2);
//        return EuclideanDistance(u1, u2);
    }

    /**
     * Get a users playcount of an artist
     * @param array Users artist array
     * @param x Artist name
     * @return Users playcount of given artist
     */
    static int getPlaycount(final ArrayList<Artist> array, String x) {
        for (Artist a : array) {
            if (a.name.equals(x)) {
//                return 1;
                return a.playcount;
            }
        }
        return 0;
    }

    static double EuclideanDistance(int[] vecA, int[] vecB) {
        double distance = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            distance += Math.pow((vecA[i] - vecB[i]), 2);
        }

        return distance/100;
    }

    /**
     * Calculate cosine similarity of two vectors
     * @param vecA playcounts of userA artists
     * @param vecB playcounts of userB artists
     * @return cosine similarity value
     */
    static double cosineSimilarity(int[] vecA, int[] vecB) {
        double dotProd = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dotProd += vecA[i] * vecB[i];
            normA += Math.pow(vecA[i], 2);
            normB += Math.pow(vecB[i], 2);
        }

        return dotProd / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
