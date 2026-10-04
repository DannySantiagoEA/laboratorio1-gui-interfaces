/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.model.EscritorArchivo;
import com.laboratorio.view.GraficaTiempo;
import com.laboratorio.view.VentanaPrincipal;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Controlador principal de la arquitectura MVC (HU-01 a HU-07). Orquesta la
 * temporización periódica desacoplada, la actualización de las series gráficas
 * analógicas y digitales en tiempo real, la sanitización del periodo Ts y la
 * persistencia a disco de ambos dominios de señal.
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial estándar: 100 ms (10 Hz)
    private long contadorTicks = 0;

    // Componentes gráficos JFreeChart encapsulados en GraficaTiempo
    private GraficaTiempo graficaAnalogica;
    private GraficaTiempo graficaDigital;

    // Estado de selección de canales activos en la GUI
    private int canalAnalogicoActivo = 0;
    private int canalDigitalActivo = 0;

    // Búfer en memoria para canal analógico activo (HU-04 / #21)
    private final List<double[]> bufferAnalogicoActivo = Collections.synchronizedList(new ArrayList<>());

    // Búfer en memoria para canal digital activo (HU-06 / #30)
    private final List<double[]> bufferDigitalActivo = Collections.synchronizedList(new ArrayList<>());

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        inicializarGraficas();
        inicializarVista();
        configurarTimer();
        conectarEventos();
    }

    /**
     * Construye las instancias de GraficaTiempo para analógica y digital.
     */
    private void inicializarGraficas() {
        // Señal analógica: trazo continuo, rango 0.0 V a 5.0 V, color rojo
        this.graficaAnalogica = new GraficaTiempo("Voltaje (V)", 0.0, 5.0, false, new Color(200, 30, 30));

        // Señal digital: escalón discreto, rango -0.2 a 1.2, color azul
        this.graficaDigital = new GraficaTiempo("Nivel Lógico", -0.2, 1.2, true, new Color(30, 110, 200));
    }

    /**
     * Monta los lienzos en la interfaz y sincroniza los combos y etiquetas
     * iniciales.
     */
    private void inicializarVista() {
        vista.montarGraficaAnalogica(this.graficaAnalogica);
        vista.montarGraficaDigital(this.graficaDigital);

        vista.cargarNombresCanales(modelo.getNombresAnalogicas(), modelo.getNombresDigitales());
        vista.setTiempoMuestreoActual(this.periodoMuestreoMs);
    }

    /**
     * Inicializa el temporizador Swing para emitir muestreos periódicos en el
     * EDT.
     */
    private void configurarTimer() {
        timerMuestreo = new Timer(periodoMuestreoMs, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ejecutarCicloMuestreo();
            }
        });
    }

    /**
     * Tarea periódica: avanza el modelo, actualiza los lienzos de JFreeChart y
     * alimenta ambos búferes cronológicos en memoria.
     */
    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        modelo.tomarMuestra();

        double[] analogicas = modelo.getTodasLasAnalogicas();
        int[] digitales = modelo.getTodasLasDigitales();
        double tiempoActual = modelo.getTiempo();

        double vAnalogico = analogicas[canalAnalogicoActivo];
        int vDigital = digitales[canalDigitalActivo];

        // 1. Renderizado dinámico en pantalla
        graficaAnalogica.agregarPunto(tiempoActual, vAnalogico);
        graficaDigital.agregarPunto(tiempoActual, vDigital);

        // 2. Acumulación ordenada en memoria de pares (ti, Vi) y (ti, Si)
        bufferAnalogicoActivo.add(new double[]{tiempoActual, vAnalogico});
        bufferDigitalActivo.add(new double[]{tiempoActual, (double) vDigital});
    }

    /**
     * Conecta la API pública de eventos de VentanaPrincipal con los
     * controladores de acción.
     */
    private void conectarEventos() {
        // Conmutación de canal analógico (HU-02)
        vista.addListenerCanalAnalogico(e -> {
            int nuevoCanal = vista.getCanalAnalogico();
            if (nuevoCanal >= 0 && nuevoCanal < Data.NUM_ANALOGICAS && nuevoCanal != canalAnalogicoActivo) {
                this.canalAnalogicoActivo = nuevoCanal;
                this.graficaAnalogica.limpiar();
                limpiarBufferAnalogico();
                System.out.println(">> Canal analógico conmutado a: " + modelo.getNombresAnalogicas()[nuevoCanal]);
            }
        });

        // Conmutación de canal digital (HU-05 / Subtarea #27)
        vista.addListenerCanalDigital(e -> {
            int nuevoCanal = vista.getCanalDigital();
            if (nuevoCanal >= 0 && nuevoCanal < Data.NUM_DIGITALES && nuevoCanal != canalDigitalActivo) {
                this.canalDigitalActivo = nuevoCanal;
                this.graficaDigital.limpiar();
                limpiarBufferDigital(); // Reinicio atómico del búfer al conmutar canal (#30)
                System.out.println(">> [Controlador] Canal digital activo: " + modelo.getNombresDigitales()[nuevoCanal]);
            }
        });

        // Actualización de Ts en caliente (HU-03)
        vista.addListenerCambiarMuestreo(e -> {
            String textoEntrada = vista.getTiempoMuestreoTexto();
            actualizarPeriodo(textoEntrada);
        });

        // Exportación de datos analógicos a disco (HU-04)
        vista.addListenerGuardarAnalogica(e -> {
            exportarDatosCanalAnalogico();
        });

        // Exportación de datos digitales a disco (HU-06)
        vista.addListenerGuardarDigital(e -> {
            exportarDatosCanalDigital();
        });
    }

    /**
     * Sanitiza y valida la entrada con ValidadorMuestreo aplicando el nuevo
     * retardo en caliente.
     */
    public boolean actualizarPeriodo(String entradaTexto) {
        try {
            int nuevoTs = ValidadorMuestreo.validarPeriodo(entradaTexto);
            this.periodoMuestreoMs = nuevoTs;

            if (timerMuestreo != null) {
                timerMuestreo.setDelay(nuevoTs);
            }
            if (modelo != null) {
                modelo.setPeriodoMuestreoMs(nuevoTs);
            }

            vista.setTiempoMuestreoActual(nuevoTs);
            System.out.println(">> [Controlador] Periodo de muestreo reconfigurado a: " + nuevoTs + " ms");
            return true;

        } catch (PeriodoInvalidoException ex) {
            JOptionPane.showMessageDialog(
                    vista,
                    ex.getMessage(),
                    "Tiempo de Muestreo Inválido",
                    JOptionPane.WARNING_MESSAGE
            );
            return false;
        }
    }

    // =========================================================================
    // PERSISTENCIA Y EXPORTACIÓN A DISCO (HU-04 Y HU-06)
    // =========================================================================
    /**
     * Abre un JFileChooser y persiste el búfer analógico en disco.
     */
    private void exportarDatosCanalAnalogico() {
        List<double[]> datos = obtenerBufferAnalogicoActivo();
        if (datos.isEmpty()) {
            JOptionPane.showMessageDialog(vista,
                    "No hay muestras analógicas en memoria para exportar.",
                    "Búfer Vacío",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File archivo = solicitarRutaGuardado("Exportar Señal Analógica a Archivo");
        if (archivo != null) {
            try {
                int total = EscritorArchivo.guardarValorVsTiempo(
                        datos,
                        archivo,
                        modelo.getNombresAnalogicas()[canalAnalogicoActivo],
                        "V"
                );
                JOptionPane.showMessageDialog(vista,
                        "Archivo analógico guardado con " + total + " muestras:\n" + archivo.getAbsolutePath(),
                        "Exportación Exitosa",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(vista,
                        "Error al escribir el archivo: " + ex.getMessage(),
                        "Error I/O",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Abre un JFileChooser y persiste el búfer digital en disco (#30 / HU-06).
     */
    private void exportarDatosCanalDigital() {
        List<double[]> datos = obtenerBufferDigitalActivo();
        if (datos.isEmpty()) {
            JOptionPane.showMessageDialog(vista,
                    "No hay muestras digitales en memoria para exportar.",
                    "Búfer Vacío",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File archivo = solicitarRutaGuardado("Exportar Señal Digital a Archivo");
        if (archivo != null) {
            try {
                int total = EscritorArchivo.guardarValorVsTiempo(
                        datos,
                        archivo,
                        modelo.getNombresDigitales()[canalDigitalActivo],
                        "Estado"
                );
                JOptionPane.showMessageDialog(vista,
                        "Archivo digital guardado con " + total + " muestras:\n" + archivo.getAbsolutePath(),
                        "Exportación Exitosa",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(vista,
                        "Error al escribir el archivo digital: " + ex.getMessage(),
                        "Error I/O",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Diálogo centralizado para selección de archivo de destino.
     */
    private File solicitarRutaGuardado(String titulo) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle(titulo);
        selector.setFileFilter(new FileNameExtensionFilter("Archivos de texto (*.txt)", "txt"));

        int seleccion = selector.showSaveDialog(vista);
        if (seleccion == JFileChooser.APPROVE_OPTION) {
            File f = selector.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".txt")) {
                f = new File(f.getAbsolutePath() + ".txt");
            }
            return f;
        }
        return null;
    }

    // =========================================================================
    // EXTRACTORES DE BÚFERES CRONOLÓGICOS (SUBTAREAS #21 Y #30)
    // =========================================================================
    /**
     * HU-04 (Subtarea #21): Retorna una copia defensiva del búfer analógico
     * activo.
     */
    public synchronized List<double[]> obtenerBufferAnalogicoActivo() {
        synchronized (bufferAnalogicoActivo) {
            return new ArrayList<>(bufferAnalogicoActivo);
        }
    }

    /**
     * HU-06 (Subtarea #30): Retorna una copia defensiva del búfer digital
     * activo en pares (tiempo, estado). Cada registro es un double[] donde [0]
     * = tiempo en segundos y [1] = nivel lógico (0.0 o 1.0).
     */
    public synchronized List<double[]> obtenerBufferDigitalActivo() {
        synchronized (bufferDigitalActivo) {
            return new ArrayList<>(bufferDigitalActivo);
        }
    }

    public int getCantidadMuestrasAnalogicas() {
        return bufferAnalogicoActivo.size();
    }

    public int getCantidadMuestrasDigitales() {
        return bufferDigitalActivo.size();
    }

    public void limpiarBufferAnalogico() {
        bufferAnalogicoActivo.clear();
    }

    public void limpiarBufferDigital() {
        bufferDigitalActivo.clear();
    }

    // =========================================================================
    // CONTROL DEL CICLO DE VIDA
    // =========================================================================
    public void iniciarAdquisicion() {
        if (timerMuestreo != null && !timerMuestreo.isRunning()) {
            timerMuestreo.start();
            System.out.println(">> Adquisición en tiempo real iniciada.");
        }
    }

    public void detenerAdquisicion() {
        if (timerMuestreo != null && timerMuestreo.isRunning()) {
            timerMuestreo.stop();
            System.out.println(">> Adquisición detenida.");
        }
    }

    public boolean isEjecutando() {
        return timerMuestreo != null && timerMuestreo.isRunning();
    }

    public int getPeriodoMuestreoMs() {
        return periodoMuestreoMs;
    }

    public int getCanalAnalogicoActivo() {
        return canalAnalogicoActivo;
    }

    public int getCanalDigitalActivo() {
        return canalDigitalActivo;
    }
}
