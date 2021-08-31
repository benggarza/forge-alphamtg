package forge.adventure.world;

import forge.adventure.util.SaveFileContent;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class PointOfIntrestMap implements SaveFileContent {

    private int numberOfChunksX;
    private int numberOfChunksY;
    int tileSize;
    int chunkSize;
    private List<PointOfInterest>[][] mapObjects;

    PointOfIntrestMap(int chunkSize, int tiles, int numberOfChunksX, int numberOfChunksY) {
        this.tileSize = tiles;
        this.chunkSize = chunkSize;
        this.numberOfChunksX = numberOfChunksX;
        this.numberOfChunksY = numberOfChunksY;
        mapObjects = new List[numberOfChunksX][numberOfChunksY];
        for (int x = 0; x < numberOfChunksX; x++) {
            for (int y = 0; y < numberOfChunksY; y++) {
                mapObjects[x][y] = new ArrayList();
            }
        }
    }



    public void add(PointOfInterest obj) {
        int chunkX = (int) ((obj.position.x / tileSize) / chunkSize);
        int chunkY = (int) ((obj.position.y / tileSize) / chunkSize);
        if (chunkX >= numberOfChunksX || chunkY >= numberOfChunksY || chunkX < 0 || chunkY < 0)
            return;
        mapObjects[chunkX][chunkY].add(obj);
    }

    public List<PointOfInterest> pointsOfIntrest(int chunkX, int chunky) {
        return mapObjects[chunkX][chunky];
    }

    @Override
    public void writeToSaveFile(ObjectOutputStream saveFile) throws IOException {
        saveFile.writeInt(numberOfChunksX);
        saveFile.writeInt(numberOfChunksY);
        saveFile.writeInt(tileSize);
        saveFile.writeInt(chunkSize);
        for (int x = 0; x < numberOfChunksX; x++) {
            for (int y = 0; y < numberOfChunksY; y++) {
                saveFile.writeInt(mapObjects[x][y].size());
                for(PointOfInterest poi:mapObjects[x][y])
                {
                    poi.writeToSaveFile(saveFile);
                }
            }
        }
    }

    @Override
    public void readFromSaveFile(ObjectInputStream saveFile) throws IOException, ClassNotFoundException {
        numberOfChunksX=saveFile.readInt();
        numberOfChunksY=saveFile.readInt();
        tileSize=saveFile.readInt();
        chunkSize=saveFile.readInt();

        mapObjects = new List[numberOfChunksX][numberOfChunksY];
        for (int x = 0; x < numberOfChunksX; x++) {
            for (int y = 0; y < numberOfChunksY; y++) {
                mapObjects[x][y] = new ArrayList();
                int arraySize=saveFile.readInt();
                for(int i=0;i<arraySize;i++)
                {
                    PointOfInterest pointsOfIntrest=new PointOfInterest();
                    pointsOfIntrest.readFromSaveFile(saveFile);
                    mapObjects[x][y].add(pointsOfIntrest);
                }
            }
        }
    }
}
