package me.pepe.ServerClientAPI.Utils;

public interface RedMaskFindedCallback<T> {
    void done(T result, Exception exception);
}
