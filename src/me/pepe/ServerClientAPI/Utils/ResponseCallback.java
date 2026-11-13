package me.pepe.ServerClientAPI.Utils;
public interface ResponseCallback<T> { void done(T result, Exception failure); }
