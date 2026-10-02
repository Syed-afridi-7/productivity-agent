import 'package:flutter/material.dart';

class CyberTheme {
  // OLED & Surface Colors
  static const Color bgOled = Color(0xFF07080D);
  static const Color cardBg = Color(0xFF11131F);
  static const Color borderSubtle = Color(0xFF1E2235);
  static const Color borderActive = Color(0xFF2E344D);

  // Neon Accents
  static const Color neonEmerald = Color(0xFF00F5A0);
  static const Color amberGlow = Color(0xFFFFB800);
  static const Color crimsonNeon = Color(0xFFFF3366);
  static const Color electricCyan = Color(0xFF00D2FF);

  // Text Colors
  static const Color textPrimary = Color(0xFFFFFFFF);
  static const Color textMuted = Color(0xFF8E95A5);

  static ThemeData get darkTheme {
    return ThemeData(
      brightness: Brightness.dark,
      scaffoldBackgroundColor: bgOled,
      primaryColor: neonEmerald,
      cardColor: cardBg,
      fontFamily: 'monospace',
      appBarTheme: const AppBarTheme(
        backgroundColor: bgOled,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: textPrimary,
          fontSize: 16,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.5,
          fontFamily: 'monospace',
        ),
      ),
      colorScheme: const ColorScheme.dark(
        surface: cardBg,
        primary: neonEmerald,
        secondary: electricCyan,
        error: crimsonNeon,
        onSurface: textPrimary,
      ),
    );
  }
}
