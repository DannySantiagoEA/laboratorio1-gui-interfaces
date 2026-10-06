/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.laboratorio.controller;

import com.laboratorio.model.Data;
import com.laboratorio.model.EscritorArchivo;
import com.laboratorio.model.SalidasDigitales;
import com.laboratorio.view.GraficaTiempo;
import com.laboratorio.view.IndicadorLed;
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
 * Controlador principal de la arquitectura MVC (Sprint 1 y Sprint 2). Orquesta
 * la temporización periódica desacoplada, la actualización de series continuas
 * y discretas en tiempo real, la sanitización del periodo Ts, la persistencia
 * en disco y la conmutación de salidas digitales con retroalimentación LED
 * (HU-07)[cite: 1, 14].
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial: 100 ms (SOLO afecta a las digitales)
    private long contadorTicks = 0;

    // Componentes gráficos JFreeChart encapsulados en GraficaTiempo
    private GraficaTiempo graficaAnalogica;
    private GraficaTiempo graficaDigital;

    // Estado de selección de canales activos en la GUI
    private int canalAnalogicoActivo = 0;
    private int canalDigitalActivo = 0;

    // Búferes sincronizados en memoria para exportación (HU-04 / #21 y HU-06 / #30)[cite: 1, 14]
    private final List<double[]> bufferAnalogicoActivo = Collections.synchronizedList(new ArrayList<>());
    private final List<double[]> bufferDigitalActivo = Collections.synchronizedList(new ArrayList<>());

    // Módulo de actuadores virtuales: el estado vive en el modelo (Tarea #35)
    public static final int NUM_SALIDAS = SalidasDigitales.NUM_SALIDAS;

    public ControladorAdquisicion(VentanaPrincipal vista, Data modelo) {
        this.vista = vista;
        this.modelo = modelo;

        this.modelo.setPeriodoMuestreoMs(this.periodoMuestreoMs);

        inicializarGraficas();
        inicializarVista();
        configurarTimer();
        conectarEventos();
    }

    private void inicializarGraficas() {
        // Señal analógica: trazo continuo, rango 0.0 V a 5.0 V, color rojo
        this.graficaAnalogica = new GraficaTiempo("Voltaje (V)", 0.0, 5.0, false, new Color(200, 30, 30));

        // Señal digital: puntos en tiempo discreto (una muestra cada Ts), color azul
        this.graficaDigital = new GraficaTiempo("Valor (0/1 lógico o V)", -0.2, 5.2, true, new Color(30, 110, 200));
    }

    private void inicializarVista() {
        vista.montarGraficaAnalogica(this.graficaAnalogica);
        vista.montarGraficaDigital(this.graficaDigital);

        vista.cargarNombresCanales(modelo.getNombresAnalogicas(), modelo.getNombresDigitales());
        vista.setTiempoMuestreoActual(this.periodoMuestreoMs);

        // Títulos iniciales de las gráficas
        graficaAnalogica.cambiarTitulo(modelo.getNombresAnalogicas()[canalAnalogicoActivo]);
        graficaDigital.cambiarTitulo(modelo.getNombresDigitales()[canalDigitalActivo]);

        // Estado inicial de los LED según el modelo de salidas
        SalidasDigitales salidas = modelo.getSalidas();
        for (int i = 0; i < NUM_SALIDAS; i++) {
            IndicadorLed led = vista.getLedSalida(i);
            if (led != null) {
                led.setEstado(salidas.getEstado(i));
            }
        }

        // Disparador (Tarea #35): cada vez que una salida cambia en el modelo,
        // se actualizan su LED y su botón en la vista
        salidas.agregarDisparador((canal, estado, tiempo) -> {
            IndicadorLed led = vista.getLedSalida(canal);
            if (led != null) {
                led.setEstado(estado);
            }
            if (vista.getBotonSalida(canal) != null) {
                vista.getBotonSalida(canal).setSelected(estado);
            }
            System.out.printf(">> [Actuador] t = %.2f s | Salida D%d -> %s%n",
                    tiempo, (canal + 1), (estado ? "1 (ON)" : "0 (OFF)"));
        });
    }

    private void configurarTimer() {
        // El timer va a paso fijo (analógicas continuas); Ts solo se aplica a las digitales
        timerMuestreo = new Timer(Data.PERIODO_ANALOGICO_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ejecutarCicloMuestreo();
            }
        });
    }

    private void ejecutarCicloMuestreo() {
        contadorTicks++;
        double tiempoActual = modelo.getTiempo();   // instante de ESTA muestra (antes de que avance)
        modelo.tomarMuestra();

        // 1. Analógica: un punto en cada ciclo (trazo continuo)
        double vAnalogico = modelo.getTodasLasAnalogicas()[canalAnalogicoActivo];
        graficaAnalogica.agregarPunto(tiempoActual, vAnalogico);
        bufferAnalogicoActivo.add(new double[]{tiempoActual, vAnalogico});

        // 2. Digital: solo las muestras tomadas en los instantes n·Ts (puntos)
        for (double[] fila : modelo.extraerMuestrasDigitalesNuevas()) {
            double tn = fila[0];
            double vDigital = fila[1 + canalDigitalActivo];
            graficaDigital.agregarPunto(tn, vDigital);
            bufferDigitalActivo.add(new double[]{tn, vDigital});
        }
    }

    private void conectarEventos() {
        // Conmutación de canal analógico (HU-02)[cite: 1]
        vista.addListenerCanalAnalogico(e -> {
            int nuevoCanal = vista.getCanalAnalogico();
            if (nuevoCanal >= 0 && nuevoCanal < Data.NUM_ANALOGICAS && nuevoCanal != canalAnalogicoActivo) {
                this.canalAnalogicoActivo = nuevoCanal;
                // La gráfica NO se borra: la señal anterior queda y la nueva sigue desde ahí
                this.graficaAnalogica.cambiarTitulo(modelo.getNombresAnalogicas()[nuevoCanal]);
                limpiarBufferAnalogico();
                System.out.println(">> Canal analógico conmutado a: " + modelo.getNombresAnalogicas()[nuevoCanal]);
            }
        });

        // Conmutación de canal digital (HU-05 / Subtarea #27)[cite: 1]
        vista.addListenerCanalDigital(e -> {
            int nuevoCanal = vista.getCanalDigital();
            if (nuevoCanal >= 0 && nuevoCanal < Data.NUM_DIGITALES && nuevoCanal != canalDigitalActivo) {
                this.canalDigitalActivo = nuevoCanal;
                // La gráfica NO se borra: la señal anterior queda y la nueva sigue desde ahí
                this.graficaDigital.cambiarTitulo(modelo.getNombresDigitales()[nuevoCanal]);
                limpiarBufferDigital();
                System.out.println(">> [Controlador] Canal digital activo: " + modelo.getNombresDigitales()[nuevoCanal]);
            }
        });

        // Modificación de Ts en caliente (HU-03 / Subtarea #18)[cite: 1]
        vista.addListenerCambiarMuestreo(e -> {
            String textoEntrada = vista.getTiempoMuestreoTexto();
            actualizarPeriodo(textoEntrada);
        });

        // Exportación de datos analógicos a disco (HU-04)[cite: 1]
        vista.addListenerGuardarAnalogica(e -> exportarDatosCanalAnalogico());

        // Exportación de datos digitales a disco (HU-06)[cite: 1]
        vista.addListenerGuardarDigital(e -> exportarDatosCanalDigital());

        // Conexión de los 4 actuadores y sus indicadores LED (HU-07 / Subtareas #34 y #35)[cite: 1]
        for (int i = 0; i < NUM_SALIDAS; i++) {
            final int canal = i;
            vista.addListenerSalida(canal, e -> {
                boolean estadoActual = vista.getBotonSalida(canal).isSelected();
                establecerEstadoSalida(canal, estadoActual);
            });
        }
    }

    // =========================================================================
    // SALIDAS DIGITALES Y ACTUADORES VIRTUALES (HU-07 / #34 Y #35)
    // =========================================================================
    /**
     * Cambia una salida en el MODELO. El disparador registrado en
     * inicializarVista() se encarga de actualizar el LED.
     */
    public void establecerEstadoSalida(int canal, boolean nuevoEstado) {
        if (canal >= 0 && canal < NUM_SALIDAS) {
            modelo.getSalidas().setEstado(canal, nuevoEstado, modelo.getTiempo());
        }
    }

    public boolean getEstadoSalida(int canal) {
        if (canal >= 0 && canal < NUM_SALIDAS) {
            return modelo.getSalidas().getEstado(canal);
        }
        return false;
    }

    public boolean[] getTodosLosEstadosSalidas() {
        return modelo.getSalidas().getEstados();
    }

    // =========================================================================
    // SANITIZACIÓN Y CONFIGURACIÓN DEL PERIODO TS
    // =========================================================================
    public boolean actualizarPeriodo(String entradaTexto) {
        try {
            int nuevoTs = ValidadorMuestreo.validarPeriodo(entradaTexto);
            this.periodoMuestreoMs = nuevoTs;

            // Ts solo cambia el muestreo de las digitales (el timer sigue a paso fijo)
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
    private void exportarDatosCanalAnalogico() {
        // Se guarda lo que se ve en la gráfica (puede incluir cambios de señal)
        List<double[]> datos = graficaAnalogica.getPuntosVisibles();
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
                        graficaAnalogica.getDescripcionVisible(),
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

    private void exportarDatosCanalDigital() {
        // Se guarda lo que se ve en la gráfica (puede incluir cambios de señal)
        List<double[]> datos = graficaDigital.getPuntosVisibles();
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
                        graficaDigital.getDescripcionVisible(),
                        "0/1 o V"
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
    public synchronized List<double[]> obtenerBufferAnalogicoActivo() {
        synchronized (bufferAnalogicoActivo) {
            return new ArrayList<>(bufferAnalogicoActivo);
        }
    }

    public synchronized List<double[]> obtenerBufferDigitalActivo() {
        synchronized (bufferDigitalActivo) {
            return new ArrayList<>(bufferDigitalActivo);
        }
    }

    public void limpiarBufferAnalogico() {
        bufferAnalogicoActivo.clear();
    }

    public void limpiarBufferDigital() {
        bufferDigitalActivo.clear();
    }

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
