import 'package:flutter/material.dart';
import '../core/theme.dart';

class MetricsCard extends StatelessWidget {
  final String label;
  final String value;
  final Color accentColor;
  final IconData icon;

  const MetricsCard({
    Key? key,
    required this.label,
    required this.value,
    required this.accentColor,
    required this.icon,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
        decoration: BoxDecoration(
          color: CyberTheme.cardBg,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: CyberTheme.borderSubtle),
        ),
        child: Column(
          children: [
            Icon(icon, size: 20, color: accentColor),
            const SizedBox(height: 6),
            Text(
              value,
              style: TextStyle(
                color: CyberTheme.textPrimary,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(
                color: CyberTheme.textMuted,
                fontSize: 11,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
