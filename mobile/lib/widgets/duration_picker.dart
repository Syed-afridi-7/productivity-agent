import 'package:flutter/material.dart';
import '../core/theme.dart';

class DurationPicker extends StatelessWidget {
  final int selectedMinutes;
  final bool isEnabled;
  final ValueChanged<int> onDurationSelected;

  const DurationPicker({
    Key? key,
    required this.selectedMinutes,
    required this.isEnabled,
    required this.onDurationSelected,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final durations = [25, 45, 60];

    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: durations.map((mins) {
        final isSelected = selectedMinutes == mins;
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 6),
          child: InkWell(
            onTap: isEnabled ? () => onDurationSelected(mins) : null,
            borderRadius: BorderRadius.circular(8),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
              decoration: BoxDecoration(
                color: isSelected ? CyberTheme.neonEmerald.withOpacity(0.15) : CyberTheme.cardBg,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isSelected ? CyberTheme.neonEmerald : CyberTheme.borderSubtle,
                  width: 1.5,
                ),
              ),
              child: Text(
                '${mins}m',
                style: TextStyle(
                  color: isSelected ? CyberTheme.neonEmerald : CyberTheme.textMuted,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  fontSize: 14,
                ),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }
}
