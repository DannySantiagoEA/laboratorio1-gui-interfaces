/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import com.laboratorio.modelo.Data;

/**
 * Ventana con un menú para las 8 señales analógicas y otro para las 4 digitales.
 * Solo se muestra el valor de la señal escogida en cada menú.
 */
public class VentanaPrincipal extends JFrame {

    private Data modelo;
    private Timer timer;

    // Menús desplegables (se llenan con los nombres que da el modelo)
    private JComboBox<String> cmbAnalogicas;
    private JComboBox<String> cmbDigitales;

    private JLabel lblTiempo = new JLabel("Tiempo: 0.00 s");
    private JProgressBar barraAnalogica = new JProgressBar(0, 500);   // 500 = 5.00 V
    private JLabel ledDigital = new JLabel("0", SwingConstants.CENTER);

    public VentanaPrincipal(Data modelo) {
        this.modelo = modelo;
        cmbAnalogicas = new JComboBox<>(modelo.getNombresAnalogicas());   // 8 opciones
        cmbDigitales = new JComboBox<>(modelo.getNombresDigitales());     // 4 opciones

        setTitle("Laboratorio Virtual - Generador de funciones");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        add(crearPanelSuperior(), BorderLayout.NORTH);
        add(crearPanelSenales(), BorderLayout.CENTER);

        configurarEventos();

        pack();
        setLocationRelativeTo(null);
        timer.start();   // empieza a muestrear apenas abre la ventana
    }

    // ===================== CONSTRUCCIÓN DE LA PANTALLA =====================

    private JPanel crearPanelSuperior() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        panel.add(new JLabel(String.format("Frecuencia: %.1f Hz", modelo.getFrecuencia())));
        panel.add(lblTiempo);
        return panel;
    }

    private JPanel crearPanelSenales() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));

        // --- Analógica: menú + barra con el voltaje ---
        JPanel pnlA = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        pnlA.setBorder(BorderFactory.createTitledBorder("Entradas analógicas (8)"));
        barraAnalogica.setStringPainted(true);
        barraAnalogica.setPreferredSize(new Dimension(300, 26));
        pnlA.add(new JLabel("Señal:"));
        pnlA.add(cmbAnalogicas);
        pnlA.add(barraAnalogica);

        // --- Digital: menú + "LED" ---
        JPanel pnlD = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        pnlD.setBorder(BorderFactory.createTitledBorder("Entradas digitales (4)"));
        ledDigital.setOpaque(true);
        ledDigital.setPreferredSize(new Dimension(60, 26));
        pnlD.add(new JLabel("Señal:"));
        pnlD.add(cmbDigitales);
        pnlD.add(ledDigital);

        panel.add(pnlA);
        panel.add(pnlD);
        return panel;
    }

    // ===================== EVENTOS =====================

    private void configurarEventos() {
        // Al escoger una analógica, esa señal arranca desde ahora
        cmbAnalogicas.addActionListener(e ->
                modelo.reiniciarAnalogica(cmbAnalogicas.getSelectedIndex()));

        // Al escoger una digital, esa señal arranca desde ahora
        cmbDigitales.addActionListener(e ->
                modelo.reiniciarDigital(cmbDigitales.getSelectedIndex()));

        // Timer: cada periodo de muestreo toma una muestra y actualiza la pantalla
        timer = new Timer(modelo.getPeriodoMuestreoMs(), e -> {
            modelo.tomarMuestra();
            actualizarPantalla();
        });
    }

    /** Muestra SOLO las señales escogidas en los menús. */
    private void actualizarPantalla() {
        int canalA = cmbAnalogicas.getSelectedIndex();   // 0 a 7
        int canalD = cmbDigitales.getSelectedIndex();    // 0 a 3

        double v = modelo.getAnalogica(canalA);
        barraAnalogica.setValue((int) Math.round(v * 100));
        barraAnalogica.setString(String.format("%.2f V", v));

        boolean encendido = modelo.getDigital(canalD) == 1;
        ledDigital.setText(encendido ? "1" : "0");
        ledDigital.setBackground(encendido ? new Color(40, 180, 70) : Color.LIGHT_GRAY);

        lblTiempo.setText(String.format("Tiempo: %.2f s", modelo.getTiempo()));
    }
}