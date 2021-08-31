package forge.adventure;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import forge.adventure.libgdxgui.Graphics;
import forge.adventure.scene.ForgeScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.SceneType;
import forge.adventure.util.Res;

public class AdventureApplicationAdapter extends ApplicationAdapter {
    public static AdventureApplicationAdapter instance;
    String strPlane;
    Scene currentScene = null;
    Array<Scene> lastScene = new Array<>();
    private int currentWidth;
    private int currentHeight;
    private float animationTimeout;
    private final float transitionTime=0.2f;
    Batch animationBatch;
    Texture transitionTexture;
    TextureRegion lastScreenTexture;
    private boolean sceneWasSwaped=false;
    private Graphics graphics;

    public Graphics getGraphics()
    {
        if(graphics==null)
            graphics=new Graphics();
        return graphics;
    }

    public TextureRegion getLastScreenTexture() {
        return lastScreenTexture;
    }
    public AdventureApplicationAdapter(String plane) {
        instance = this;
        strPlane = plane;
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }


    public Scene getCurrentScene() {
        return currentScene;
    }

    @Override
    public void resize(int w, int h) {
        currentWidth = w;
        currentHeight = h;
        StartAdventure.app.resize(w, h);
        super.resize(w, h);
    }

    public boolean switchScene(Scene newScene) {

        if (currentScene != null) {
            if (!currentScene.leave())
                return false;
            lastScene.add(currentScene);
        }
        storeScreen();
        sceneWasSwaped=true;
        currentScene = newScene;
        currentScene.enter();
        return true;
    }

    private void storeScreen() {
         if(!(currentScene instanceof ForgeScene))
            lastScreenTexture = ScreenUtils.getFrameBufferTexture();


    }

    public void resLoaded() {
        for (forge.adventure.scene.SceneType entry : SceneType.values()) {
            entry.instance.resLoaded();
        }
        //AdventureApplicationAdapter.CurrentAdapter.switchScene(SceneType.RewardScene.instance);


        switchScene(SceneType.StartScene.instance);
        animationBatch=new SpriteBatch();
        transitionTexture =new Texture(Res.CurrentRes.GetFile("ui/transition.png"));
    }


    @Override
    public void create() {

        Pixmap pm = new Pixmap(Res.CurrentRes.GetFile("skin/cursor.png"));
        Gdx.graphics.setCursor(Gdx.graphics.newCursor(pm, 0, 0));
        pm.dispose();
        for (forge.adventure.scene.SceneType entry : SceneType.values()) {
            entry.instance.create();
        }
    }

    @Override
    public void render() {
        float delta=Gdx.graphics.getDeltaTime();
        if(sceneWasSwaped)
        {
            sceneWasSwaped=false;
            animationTimeout=transitionTime;
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            return;
        }
        if(animationTimeout>=0)
        {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            animationBatch.begin();
            animationTimeout-=delta;
            animationBatch.setColor(1,1,1,1);
            animationBatch.draw(lastScreenTexture,0,0, Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            animationBatch.setColor(1,1,1,1-(1/transitionTime)*animationTimeout);
            animationBatch.draw(transitionTexture,0,0, Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            animationBatch.draw(transitionTexture,0,0, Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            animationBatch.end();
            if(animationTimeout<0)
            {
                currentScene.render();
                storeScreen();
                Gdx.gl.glClearColor(0, 0, 0, 1);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            }
            else
            {
                return;
            }
        }
        if(animationTimeout>=-transitionTime)
        {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            animationBatch.begin();
            animationTimeout-=delta;
            animationBatch.setColor(1,1,1,1);
            animationBatch.draw(lastScreenTexture,0,0, Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            animationBatch.setColor(1,1,1,(1/transitionTime)*(animationTimeout+transitionTime));
            animationBatch.draw(transitionTexture,0,0, Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            animationBatch.draw(transitionTexture,0,0, Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            animationBatch.end();
            return;
        }
        currentScene.render();
        currentScene.act(delta);
    }

    @Override
    public void dispose() {
        for (forge.adventure.scene.SceneType entry : SceneType.values()) {
            entry.instance.dispose();
        }
    }

    private Scene getLastScene() {
        return lastScene.size==0?null: lastScene.get(lastScene.size-1);
    }

    public Scene switchToLast() {

        if(lastScene.size!=0)
        {
            storeScreen();
            currentScene = lastScene.get(lastScene.size-1);
            currentScene.enter();
            sceneWasSwaped=true;
            lastScene.removeIndex(lastScene.size-1);
            return currentScene;
        }
        return null;
    }

}
