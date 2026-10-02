import 'package:flutter/material.dart';
import 'package:productivity_agent/screens/settings_screen.dart';
import '../controllers/agent_controller.dart';
import '../core/theme.dart';
import '../widgets/duration_picker.dart';
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

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final status = controller.status;
        final isRunning = status.isRunning;

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

                  // Daily Autonomous Quotas Section
                  _buildQuotaCard(
                    title: 'Gaming Budget',
                    usageText: '${status.gamingMinutesUsed} / ${status.gamingLimitMinutes}m',
                    progress: status.gamingProgress,
                    accentColor: CyberTheme.electricCyan,
                    icon: Icons.sports_esports_outlined,
                  ),

                  const SizedBox(height: 10),

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

                  const SizedBox(height: 24),

                  // Duration Selector for manual focus sprints
                  DurationPicker(
                    selectedMinutes: controller.selectedDurationMinutes,
                    isEnabled: !isRunning,
                    onDurationSelected: (mins) => controller.selectDuration(mins),
                  ),

                  const SizedBox(height: 20),

                  // Primary Action Button
                  SizedBox(
                    height: 54,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isRunning
                            ? CyberTheme.bgOled
                            : CyberTheme.neonEmerald,
                        foregroundColor: isRunning
                            ? CyberTheme.crimsonNeon
                            : CyberTheme.bgOled,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                          side: BorderSide(
                            color: isRunning
                                ? CyberTheme.crimsonNeon
                                : CyberTheme.neonEmerald,
                            width: 2.0,
                          ),
                        ),
                        elevation: isRunning ? 0 : 8,
                      ),
                      onPressed: controller.isLoading ? null : () => controller.toggleAgent(),
                      child: controller.isLoading
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(
                              isRunning ? 'HALT SESSION' : 'START GUARDIAN',
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1.5,
                              ),
                            ),
                    ),
                  ),

                  const SizedBox(height: 28),

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
