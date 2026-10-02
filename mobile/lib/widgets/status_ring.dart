import 'package:flutter/material.dart';
import '../core/theme.dart';
import '../models/agent_status.dart';

class StatusRing extends StatelessWidget {
  final AgentStatus status;

  const StatusRing({
    Key? key,
    required this.status,
  }) : super(key: key);

  Color get _accentColor => CyberTheme.neonEmerald;

  @override
  Widget build(BuildContext context) {
    final windowName = status.currentWindow.toUpperCase();
    final remainingMins = status.currentWindowRemainingMinutes;

    return Center(
      child: Container(
        width: 220,
        height: 220,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: CyberTheme.cardBg,
          border: Border.all(
            color: _accentColor.withOpacity(0.8),
            width: 3.0,
          ),
          boxShadow: [
            BoxShadow(
              color: _accentColor.withOpacity(0.35),
              blurRadius: 28,
              spreadRadius: 2,
            ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.shield_rounded,
              color: CyberTheme.neonEmerald,
              size: 46,
            ),
            const SizedBox(height: 8),
            const Text(
              'GUARDIAN',
              style: TextStyle(
                color: CyberTheme.textPrimary,
                fontSize: 22,
                fontWeight: FontWeight.w900,
                letterSpacing: 2.5,
              ),
            ),
            const Text(
              'ACTIVE 24/7',
              style: TextStyle(
                color: CyberTheme.neonEmerald,
                fontSize: 13,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.8,
              ),
            ),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: _accentColor.withOpacity(0.15),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                '$windowName • ${remainingMins}M LEFT',
                style: TextStyle(
                  color: _accentColor,
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
