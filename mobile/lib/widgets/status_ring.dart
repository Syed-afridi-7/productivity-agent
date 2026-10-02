import 'package:flutter/material.dart';
import '../core/theme.dart';
import '../models/agent_status.dart';

class StatusRing extends StatelessWidget {
  final AgentStatus status;

  const StatusRing({
    Key? key,
    required this.status,
  }) : super(key: key);

  Color get _accentColor {
    switch (status.state) {
      case AgentState.deepFocus:
        return CyberTheme.neonEmerald;
      case AgentState.shortBreak:
        return CyberTheme.amberGlow;
      case AgentState.idle:
        return CyberTheme.borderActive;
    }
  }

  @override
  Widget build(BuildContext context) {
    final displayText = status.isRunning ? status.formattedTime : 'READY';

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
            if (status.isRunning)
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
            Text(
              displayText,
              style: TextStyle(
                color: CyberTheme.textPrimary,
                fontSize: status.isRunning ? 40 : 34,
                fontWeight: FontWeight.w900,
                letterSpacing: 2.0,
              ),
            ),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: _accentColor.withOpacity(0.15),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                status.stateDisplayName,
                style: TextStyle(
                  color: _accentColor,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
