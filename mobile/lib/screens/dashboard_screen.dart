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

                  const SizedBox(height: 16),

                  // Hero Status Ring
                  StatusRing(status: status),

                  const SizedBox(height: 32),

                  // Duration Selector
                  DurationPicker(
                    selectedMinutes: controller.selectedDurationMinutes,
                    isEnabled: !isRunning,
                    onDurationSelected: (mins) => controller.selectDuration(mins),
                  ),

                  const SizedBox(height: 28),

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

                  const SizedBox(height: 36),

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
