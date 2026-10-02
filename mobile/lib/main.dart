import 'package:flutter/material.dart';
import 'controllers/agent_controller.dart';
import 'core/theme.dart';
import 'screens/dashboard_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final controller = AgentController();
  await controller.init();
  runApp(ProductivityApp(controller: controller));
}

class ProductivityApp extends StatelessWidget {
  final AgentController controller;

  const ProductivityApp({
    Key? key,
    required this.controller,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Productivity Agent',
      debugShowCheckedModeBanner: false,
      theme: CyberTheme.darkTheme,
      home: DashboardScreen(controller: controller),
    );
  }
}
