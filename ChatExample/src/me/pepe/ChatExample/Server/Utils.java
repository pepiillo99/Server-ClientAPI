package me.pepe.ChatExample.Server;

import java.util.Random;

public class Utils {
	public static Random random = new Random();
	private static String avalibleColors = "123456789abce";
	public static String getRandomColor() {
		return String.valueOf(avalibleColors.charAt(random.nextInt(avalibleColors.length())));
	}
}
