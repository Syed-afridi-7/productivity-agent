import 'package:flutter/material.dart';
import 'package:productivity_agent/screens/settings_screen.dart';
import '../controllers/agent_controller.dart';
import '../core/theme.dart';
import '../widgets/metrics_card.dart';
import '../widgets/status_ring.dart';

class DashboardScreen extends StatelessWidget {
  final AgentController controller;

  const DashboardScreen({
    Key? key,
    required this.controller,
  }) : super(key: key);

  DashboardScreen get widget => this;

  PreferredSizeWidget _buildHeader(BuildContext context) {
    final isRunning = controller.status.isRunning;
    return AppBar(
      title: const Text('⚡ PRODUCTIVITY AGENT'),
      actions: [
        Center(
          child: Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: isRunning ? CyberTheme.neonEmerald : CyberTheme.borderActive,
              boxShadow: [
                if (isRunning)
                  const BoxShadow(
                    color: CyberTheme.neonEmerald,
                    blurRadius: 6,
                  ),
              ],
            ),
          ),
        ),
        IconButton(
          key: const Key('dashboard_settings_button'),
          icon: const Icon(Icons.tune_rounded, color: CyberTheme.textDim, size: 22),
          onPressed: () {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (_) => SettingsScreen(controller: widget.controller),
              ),
            );
          },
        ),
        const SizedBox(width: 8),
      ],
    );
  }

  Widget _buildQuotaCard({
    required String title,
    required String usageText,
    required double progress,
    required Color accentColor,
    required IconData icon,
  }) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: CyberTheme.cardBg,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: CyberTheme.borderSubtle),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(icon, size: 18, color: accentColor),
                  const SizedBox(width: 8),
                  Text(
                    title,
                    style: const TextStyle(
                      color: CyberTheme.textPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
              Text(
                usageText,
                style: TextStyle(
                  color: accentColor,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              backgroundColor: CyberTheme.bgOled,
              valueColor: AlwaysStoppedAnimation<Color>(
                progress >= 1.0 ? CyberTheme.crimsonNeon : accentColor,
              ),
              minHeight: 6,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWindowCard({
    required String title,
    required String timeSpan,
    required int usedMinutes,
    required int limitMinutes,
    required double progress,
    required bool isActive,
    required bool isExhausted,
  }) {
    final borderColor = isActive
        ? (isExhausted ? CyberTheme.crimsonNeon : CyberTheme.electricCyan)
        : CyberTheme.borderSubtle;
    final accentColor = isActive
        ? (isExhausted ? CyberTheme.crimsonNeon : CyberTheme.electricCyan)
        : CyberTheme.textDim;

    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        decoration: BoxDecoration(
          color: isActive ? CyberTheme.cardBg : CyberTheme.cardBg.withOpacity(0.6),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: borderColor,
            width: isActive ? 1.8 : 1.0,
          ),
          boxShadow: [
            if (isActive)
              BoxShadow(
                color: borderColor.withOpacity(0.2),
                blurRadius: 8,
                spreadRadius: 1,
              ),
          ],
        ),
        child: Column(
          children: [
            if (isActive)
              Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: borderColor.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  isExhausted ? 'EXHAUSTED' : 'ACTIVE',
                  style: TextStyle(
                    color: borderColor,
                    fontSize: 9,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.8,
                  ),
                ),
              ),
            Text(
              title,
              style: TextStyle(
                color: isActive ? CyberTheme.textPrimary : CyberTheme.textDim,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              timeSpan,
              style: const TextStyle(
                color: CyberTheme.textDim,
                fontSize: 9,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '$usedMinutes / ${limitMinutes}m',
              style: TextStyle(
                color: accentColor,
                fontSize: 12,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 6),
            ClipRRect(
              borderRadius: BorderRadius.circular(3),
              child: LinearProgressIndicator(
                value: progress,
                backgroundColor: CyberTheme.bgOled,
                valueColor: AlwaysStoppedAnimation<Color>(
                  progress >= 1.0 ? CyberTheme.crimsonNeon : accentColor,
                ),
                minHeight: 4,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final status = controller.status;
        final currentWin = status.currentWindow.toLowerCase();
        final isMorningActive = currentWin.contains('morning');
        final isAfternoonActive = currentWin.contains('afternoon');
        final isEveningActive = currentWin.contains('evening');

        return Scaffold(
          appBar: _buildHeader(context),
          body: SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // 24/7 Autonomous Guardian Active Badge
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: CyberTheme.neonEmerald.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: CyberTheme.neonEmerald.withOpacity(0.4)),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 8,
                          height: 8,
                          decoration: const BoxDecoration(
                            shape: BoxShape.circle,
                            color: CyberTheme.neonEmerald,
                            boxShadow: [
                              BoxShadow(
                                color: CyberTheme.neonEmerald,
                                blurRadius: 4,
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 10),
                        const Expanded(
                          child: Text(
                            '24/7 GUARDIAN: ACTIVE',
                            style: TextStyle(
                              color: CyberTheme.neonEmerald,
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              letterSpacing: 1.2,
                            ),
                          ),
                        ),
                        const Icon(Icons.shield_outlined, color: CyberTheme.neonEmerald, size: 18),
                      ],
                    ),
                  ),

                  const SizedBox(height: 12),

                  // Accessibility Warning Banner
                  if (!controller.isAccessibilityGranted)
                    Container(
                      margin: const EdgeInsets.only(bottom: 16),
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: CyberTheme.amberGlow.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: CyberTheme.amberGlow),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.warning_amber_rounded, color: CyberTheme.amberGlow),
                          const SizedBox(width: 10),
                          const Expanded(
                            child: Text(
                              'Accessibility permission needed to enforce focus mode.',
                              style: TextStyle(color: CyberTheme.textPrimary, fontSize: 12),
                            ),
                          ),
                          TextButton(
                            onPressed: () => controller.requestAccessibility(),
                            child: const Text(
                              'ENABLE',
                              style: TextStyle(color: CyberTheme.amberGlow, fontWeight: FontWeight.bold),
                            ),
                          ),
                        ],
                      ),
                    ),

                  // Hero Status Ring
                  StatusRing(status: status),

                  const SizedBox(height: 24),

                  // ─── Entertainment Quotas Section ───
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'ENTERTAINMENT QUOTAS',
                        style: TextStyle(
                          color: CyberTheme.textDim,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1.2,
                        ),
                      ),
                      Text(
                        'Daily Limit: 60m (3x 20m)',
                        style: const TextStyle(
                          color: CyberTheme.amberGlow,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  // 3-Window Quota Breakdown (Morning / Afternoon / Evening)
                  Row(
                    children: [
                      _buildWindowCard(
                        title: 'Morning',
                        timeSpan: '06:00 - 12:00',
                        usedMinutes: status.morningMinutesUsed,
                        limitMinutes: status.windowLimitMinutes,
                        progress: status.morningProgress,
                        isActive: isMorningActive,
                        isExhausted: isMorningActive && status.isCurrentWindowExhausted,
                      ),
                      const SizedBox(width: 8),
                      _buildWindowCard(
                        title: 'Afternoon',
                        timeSpan: '12:00 - 18:00',
                        usedMinutes: status.afternoonMinutesUsed,
                        limitMinutes: status.windowLimitMinutes,
                        progress: status.afternoonProgress,
                        isActive: isAfternoonActive,
                        isExhausted: isAfternoonActive && status.isCurrentWindowExhausted,
                      ),
                      const SizedBox(width: 8),
                      _buildWindowCard(
                        title: 'Evening',
                        timeSpan: '18:00 - 24:00',
                        usedMinutes: status.eveningMinutesUsed,
                        limitMinutes: status.windowLimitMinutes,
                        progress: status.eveningProgress,
                        isActive: isEveningActive,
                        isExhausted: isEveningActive && status.isCurrentWindowExhausted,
                      ),
                    ],
                  ),

                  const SizedBox(height: 14),

                  // Gaming Budget
                  _buildQuotaCard(
                    title: 'Gaming Budget',
                    usageText: '${status.gamingMinutesUsed} / ${status.gamingLimitMinutes}m',
                    progress: status.gamingProgress,
                    accentColor: CyberTheme.electricCyan,
                    icon: Icons.sports_esports_outlined,
                  ),

                  const SizedBox(height: 10),

                  // Reels & Shorts summary card
                  _buildQuotaCard(
                    title: 'Reels & Shorts',
                    usageText: '${status.reelsMinutesUsed} / ${status.reelsLimitMinutes}m',
                    progress: status.reelsProgress,
                    accentColor: CyberTheme.amberGlow,
                    icon: Icons.video_library_outlined,
                  ),

                  const SizedBox(height: 10),

                  // Whitelisted Features Card
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    decoration: BoxDecoration(
                      color: CyberTheme.cardBg,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: CyberTheme.borderSubtle.withOpacity(0.5)),
                    ),
                    child: Row(
                      children: const [
                        Icon(Icons.check_circle_outline, color: CyberTheme.neonEmerald, size: 16),
                        SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'DMs & Study: UNLIMITED (Chat & Search unrestricted)',
                            style: TextStyle(color: CyberTheme.textDim, fontSize: 11),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 20),

                  // 24/7 Autonomous Lockdown Card (replaces manual timers and start/stop button)
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: CyberTheme.cardBg,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: CyberTheme.neonEmerald.withOpacity(0.3)),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Icon(
                          Icons.verified_user_outlined,
                          color: CyberTheme.neonEmerald,
                          size: 24,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: const [
                              Text(
                                'AUTONOMOUS LOCKDOWN ACTIVE',
                                style: TextStyle(
                                  color: CyberTheme.neonEmerald,
                                  fontSize: 13,
                                  fontWeight: FontWeight.bold,
                                  letterSpacing: 1.0,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Protection runs 24/7 with zero off-switches. Entertainment is strictly capped at 20m per window, with 12s auto-kick for unproductive content. Study and DSA are unrestricted.',
                                style: TextStyle(
                                  color: CyberTheme.textDim,
                                  fontSize: 11,
                                  height: 1.4,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  // Daily Metrics Row
                  Row(
                    children: [
                      MetricsCard(
                        label: 'Focus Today',
                        value: '${status.todayFocusMinutes}m',
                        accentColor: CyberTheme.neonEmerald,
                        icon: Icons.timer_outlined,
                      ),
                      const SizedBox(width: 8),
                      MetricsCard(
                        label: 'Blocked',
                        value: '${status.distractionsBlocked}',
                        accentColor: CyberTheme.crimsonNeon,
                        icon: Icons.block_outlined,
                      ),
                      const SizedBox(width: 8),
                      MetricsCard(
                        label: 'Nudges',
                        value: '${status.nudgesSent}',
                        accentColor: CyberTheme.electricCyan,
                        icon: Icons.explore_outlined,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
