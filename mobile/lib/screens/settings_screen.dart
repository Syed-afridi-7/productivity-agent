import 'package:flutter/material.dart';
import '../controllers/agent_controller.dart';
import '../core/theme.dart';

class SettingsScreen extends StatelessWidget {
  final AgentController controller;

  const SettingsScreen({
    super.key,
    required this.controller,
  });

  static const List<Map<String, String>> knownApps = [
    {'name': 'Instagram', 'pkg': 'com.instagram.android'},
    {'name': 'TikTok', 'pkg': 'com.zhiliaoapp.musically'},
    {'name': 'X / Twitter', 'pkg': 'com.twitter.android'},
    {'name': 'YouTube', 'pkg': 'com.google.android.youtube'},
    {'name': 'Facebook', 'pkg': 'com.facebook.katana'},
    {'name': 'Reddit', 'pkg': 'com.reddit.frontpage'},
  ];

  void _showAddPackageDialog(BuildContext context) {
    final textController = TextEditingController();
    showDialog(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        backgroundColor: CyberTheme.cardDark,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: CyberTheme.borderSubtle),
        ),
        title: Text(
          'ADD RESTRICTED PACKAGE',
          style: CyberTheme.statusLabel.copyWith(color: CyberTheme.accentEmerald),
        ),
        content: TextField(
          key: const Key('custom_package_input'),
          controller: textController,
          autofocus: true,
          style: const TextStyle(
            color: CyberTheme.textPrimary,
            fontFamily: 'monospace',
          ),
          decoration: const InputDecoration(
            hintText: 'e.g. com.example.app',
            hintStyle: TextStyle(color: CyberTheme.textDim),
            enabledBorder: UnderlineInputBorder(
              borderSide: BorderSide(color: CyberTheme.borderSubtle),
            ),
            focusedBorder: UnderlineInputBorder(
              borderSide: BorderSide(color: CyberTheme.accentEmerald),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogCtx).pop(),
            child: const Text('CANCEL', style: TextStyle(color: CyberTheme.textDim)),
          ),
          TextButton(
            key: const Key('add_package_submit'),
            onPressed: () {
              final text = textController.text.trim();
              if (text.isNotEmpty) {
                controller.addCustomPackage(text);
              }
              Navigator.of(dialogCtx).pop();
            },
            child: const Text(
              'ADD',
              style: TextStyle(
                color: CyberTheme.accentEmerald,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: CyberTheme.backgroundDark,
      appBar: AppBar(
        backgroundColor: CyberTheme.backgroundDark,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new_rounded,
            color: CyberTheme.textPrimary,
          ),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Text(
          'GUARDIAN SETTINGS',
          style: CyberTheme.statusLabel.copyWith(
            color: CyberTheme.accentEmerald,
            letterSpacing: 1.5,
          ),
        ),
      ),
      body: ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final knownPkgSet = knownApps.map((a) => a['pkg']!).toSet();
          final customApps = controller.blacklist
              .where((pkg) => !knownPkgSet.contains(pkg))
              .toList();

          return SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Section 1: SYSTEM PERMISSIONS
                Text(
                  'SYSTEM PERMISSIONS',
                  style: CyberTheme.statusLabel.copyWith(color: CyberTheme.textDim),
                ),
                const SizedBox(height: 12),
                // Accessibility Card
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: CyberTheme.cardDark,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: CyberTheme.borderSubtle),
                  ),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Accessibility Guardian',
                              style: TextStyle(
                                color: CyberTheme.textPrimary,
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                              ),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'Detects real-time distracting app launches',
                              style: TextStyle(
                                color: CyberTheme.textDim,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      if (controller.isAccessibilityGranted)
                        Chip(
                          label: const Text(
                            'ACTIVE',
                            style: TextStyle(
                              color: CyberTheme.backgroundDark,
                              fontWeight: FontWeight.bold,
                              fontSize: 11,
                            ),
                          ),
                          backgroundColor: CyberTheme.accentEmerald,
                          side: BorderSide.none,
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                        )
                      else
                        OutlinedButton(
                          onPressed: () => controller.requestAccessibility(),
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: CyberTheme.accentAmber),
                            foregroundColor: CyberTheme.accentAmber,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 8,
                            ),
                          ),
                          child: const Text(
                            'GRANT',
                            style: TextStyle(
                              color: CyberTheme.accentAmber,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                // Overlay Shield Card
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: CyberTheme.cardDark,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: CyberTheme.borderSubtle),
                  ),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Screen Overlay Shield',
                              style: TextStyle(
                                color: CyberTheme.textPrimary,
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                              ),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'Displays OLED friction overlay over blocked apps',
                              style: TextStyle(
                                color: CyberTheme.textDim,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      if (controller.isOverlayGranted)
                        Chip(
                          label: const Text(
                            'ACTIVE',
                            style: TextStyle(
                              color: CyberTheme.backgroundDark,
                              fontWeight: FontWeight.bold,
                              fontSize: 11,
                            ),
                          ),
                          backgroundColor: CyberTheme.accentEmerald,
                          side: BorderSide.none,
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                        )
                      else
                        OutlinedButton(
                          onPressed: () => controller.requestOverlayPermission(),
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: CyberTheme.accentAmber),
                            foregroundColor: CyberTheme.accentAmber,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 8,
                            ),
                          ),
                          child: const Text(
                            'GRANT',
                            style: TextStyle(
                              color: CyberTheme.accentAmber,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),

                const SizedBox(height: 28),

                // Section 2: RESTRICTED APPLICATIONS
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'RESTRICTED APPS',
                      style: CyberTheme.statusLabel.copyWith(color: CyberTheme.textDim),
                    ),
                    TextButton(
                      onPressed: () => _showAddPackageDialog(context),
                      child: const Text(
                        '+ ADD PACKAGE',
                        style: TextStyle(
                          color: CyberTheme.accentEmerald,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                // Known Apps List
                ...knownApps.map((app) {
                  final pkg = app['pkg']!;
                  final isBlacklisted = controller.isPackageBlacklisted(pkg);

                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                    decoration: BoxDecoration(
                      color: CyberTheme.cardDark,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: CyberTheme.borderSubtle),
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                app['name']!,
                                style: const TextStyle(
                                  color: CyberTheme.textPrimary,
                                  fontWeight: FontWeight.w600,
                                  fontSize: 14,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                pkg,
                                style: const TextStyle(
                                  color: CyberTheme.textDim,
                                  fontSize: 11,
                                  fontFamily: 'monospace',
                                ),
                              ),
                            ],
                          ),
                        ),
                        Switch.adaptive(
                          key: Key('switch_$pkg'),
                          value: isBlacklisted,
                          activeColor: CyberTheme.accentCrimson,
                          onChanged: (val) => controller.toggleAppBlacklist(pkg, val),
                        ),
                      ],
                    ),
                  );
                }),

                // Custom Apps List
                if (customApps.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  ...customApps.map((pkg) {
                    return Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                      decoration: BoxDecoration(
                        color: CyberTheme.cardDark,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: CyberTheme.borderSubtle),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  pkg,
                                  style: const TextStyle(
                                    color: CyberTheme.textPrimary,
                                    fontWeight: FontWeight.w600,
                                    fontSize: 13,
                                    fontFamily: 'monospace',
                                  ),
                                ),
                                const SizedBox(height: 2),
                                const Text(
                                  'Custom Package',
                                  style: TextStyle(
                                    color: CyberTheme.textDim,
                                    fontSize: 11,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            icon: const Icon(
                              Icons.delete_outline_rounded,
                              color: CyberTheme.accentCrimson,
                            ),
                            onPressed: () => controller.removeCustomPackage(pkg),
                          ),
                        ],
                      ),
                    );
                  }),
                ],

                const SizedBox(height: 28),

                // Section 3: FOCUS PREFERENCES
                Text(
                  'DEFAULT FOCUS DURATION',
                  style: CyberTheme.statusLabel.copyWith(color: CyberTheme.textDim),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [25, 45, 60].map((mins) {
                    final isSelected = controller.selectedDurationMinutes == mins;
                    return Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: InkWell(
                          onTap: () => controller.selectDuration(mins),
                          borderRadius: BorderRadius.circular(8),
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 200),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? CyberTheme.accentEmerald.withOpacity(0.15)
                                  : CyberTheme.cardDark,
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(
                                color: isSelected
                                    ? CyberTheme.accentEmerald
                                    : CyberTheme.borderSubtle,
                                width: 1.5,
                              ),
                            ),
                            child: Text(
                              '${mins}m',
                              style: TextStyle(
                                color: isSelected
                                    ? CyberTheme.accentEmerald
                                    : CyberTheme.textDim,
                                fontWeight: isSelected
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                fontSize: 14,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
