package com.deveisia.becmod.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class AnimatedTexture implements AutoCloseable {
    final ResourceLocation sprite;
    final NativeImage image;
    final int frameWidth, frameHeight;
    private final int columns;

    final int[] seqFrames, seqTimes;
    final int totalTicks;

    private AnimatedTexture(ResourceLocation sprite, NativeImage image, int frameWidth, int frameHeight,
                            int[] seqFrames, int[] seqTimes) {
        this.sprite = sprite;
        this.image = image;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.columns = Math.max(1, image.getWidth() / frameWidth);
        this.seqFrames = seqFrames;
        this.seqTimes = seqTimes;
        int t = 0;
        for (int time : seqTimes) t += time;
        this.totalTicks = t;
    }

    static AnimatedTexture load(ResourceManager rm, ResourceLocation sprite) throws IOException {
        ResourceLocation file = new ResourceLocation(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        Resource resource = rm.getResource(file).orElseThrow(() -> new FileNotFoundException(file.toString()));
        NativeImage image;
        try (InputStream in = resource.open()) {
            image = NativeImage.read(in);
        }

        JsonObject animation = null;
        Optional<Resource> meta = rm.getResource(
                new ResourceLocation(file.getNamespace(), file.getPath() + ".mcmeta"));
        if (meta.isPresent()) {
            try (Reader reader = new InputStreamReader(meta.get().open(), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject() && root.getAsJsonObject().has("animation")) {
                    animation = root.getAsJsonObject().getAsJsonObject("animation");
                }
            } catch (Exception e) {
                image.close();
                throw new IOException("Bad .mcmeta for " + file, e);
            }
        }

        try {
            return fromAnimation(sprite, image, animation);
        } catch (RuntimeException e) {
            image.close();
            throw new IOException("Bad animation data for " + file, e);
        }
    }

    private static AnimatedTexture fromAnimation(ResourceLocation sprite, NativeImage image, JsonObject anim) {
        int w = image.getWidth(), h = image.getHeight();
        if (anim == null) {
            return new AnimatedTexture(sprite, image, w, h, new int[] { 0 }, new int[] { 1 });
        }

        int frameW, frameH;
        boolean hasW = anim.has("width"), hasH = anim.has("height");
        if (hasW || hasH) {
            frameW = hasW ? anim.get("width").getAsInt() : w;
            frameH = hasH ? anim.get("height").getAsInt() : h;
        } else {
            frameW = frameH = Math.min(w, h);
        }
        if (frameW <= 0 || frameH <= 0 || w % frameW != 0 || h % frameH != 0) {
            throw new IllegalArgumentException("Image " + w + "x" + h + " not divisible into " +
                    frameW + "x" + frameH + " frames");
        }
        int frameCount = (w / frameW) * (h / frameH);
        int defaultTime = anim.has("frametime") ? Math.max(1, anim.get("frametime").getAsInt()) : 1;

        List<int[]> seq = new ArrayList<>();
        if (anim.has("frames")) {
            JsonArray frames = anim.getAsJsonArray("frames");
            for (JsonElement el : frames) {
                int index, time = defaultTime;
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    index = o.get("index").getAsInt();
                    if (o.has("time")) time = Math.max(1, o.get("time").getAsInt());
                } else {
                    index = el.getAsInt();
                }
                if (index >= 0 && index < frameCount) seq.add(new int[] { index, time });
            }
        }
        if (seq.isEmpty()) {
            for (int i = 0; i < frameCount; i++) seq.add(new int[] { i, defaultTime });
        }

        int[] frames = new int[seq.size()], times = new int[seq.size()];
        for (int i = 0; i < seq.size(); i++) {
            frames[i] = seq.get(i)[0];
            times[i] = seq.get(i)[1];
        }
        return new AnimatedTexture(sprite, image, frameW, frameH, frames, times);
    }

    int frameAt(int tick) {
        int t = Math.floorMod(tick, totalTicks);
        for (int i = 0; i < seqFrames.length; i++) {
            if (t < seqTimes[i]) return seqFrames[i];
            t -= seqTimes[i];
        }
        return seqFrames[seqFrames.length - 1];
    }

    int sample(int frame, int x, int y, int outW, int outH) {
        int fx = x * frameWidth / outW;
        int fy = y * frameHeight / outH;
        int ox = (frame % columns) * frameWidth;
        int oy = (frame / columns) * frameHeight;
        return image.getPixelRGBA(ox + fx, oy + fy);
    }

    @Override
    public void close() {
        image.close();
    }
}
