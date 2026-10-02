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
 * Controlador principal de la arquitectura MVC (HU-01, HU-02, HU-03, HU-04).
 * Orquesta la temporización periódica desacoplada, la actualización de las
 * series gráficas en tiempo real, la sanitización del periodo Ts y la
 * persistencia a disco.
 */
public class ControladorAdquisicion {

    private final VentanaPrincipal vista;
    private final Data modelo;
    private Timer timerMuestreo;
    private int periodoMuestreoMs = 100; // Ts inicial estándar: 100 ms (10 Hz)
    private long contadorTicks = 0;

    // Componentes gráficos JFreeChart encapsulados en GraficaTiempo (Tarea #13 de Brayan)
    private GraficaTiempo graficaAnalogica;
    private GraficaTiempo graficaDigital;

    // Estado de selección de canales activos en la GUI
    private int canalAnalogicoActivo = 0;
    private int canalDigitalActivo = 0;

    // Búfer sincronizado en memoria para exportación física a disco (HU-04 / Tarea #21)
    private final List<double[]> bufferMuestrasCanalActivo = Collections.synchronizedList(new ArrayList<>());

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
     * Construye las instancias de GraficaTiempo para los módulos analógico y
     * digital.
     */
    private void inicializarGraficas() {
        // Señal analógica: trazo continuo, rango 0.0 V a 5.0 V, color rojo
        this.graficaAnalogica = new GraficaTiempo("Voltaje (V)", 0.0, 5.0, false, new Color(200, 30, 30));

        // Señal digital: escalón discreto, rango -0.2 a 1.2, color azul
        this.graficaDigital = new GraficaTiempo("Nivel Lógico", -0.2, 1.2, true, new Color(30, 110, 200));
    }

    /**
     * Monta los lienzos en la interfaz de Santiago y sincroniza los combos y
     * etiquetas iniciales.
     */
    private void inicializarVista() {
        vista.montarGraficaAnalogica(this.graficaAnalogica);
        vista.montarGraficaDigital(this.graficaDigital);

        vista.cargarNombresCanales(modelo.getNombresAnalogicas(), modelo.getNombresDigitales());
        vista.setTiempoMuestreoActual(this.periodoMuestreoMs);
    }

    /**
     * Inicializa el temporizador Swing para ejecutar muestreos periódicos en el
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
     * alimenta el búfer.
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

        // 2. Acumulación ordenada de pares (ti, Vi) en el búfer temporal
        bufferMuestrasCanalActivo.add(new double[]{tiempoActual, vAnalogico});
    }

    /**
     * Conecta la API pública de eventos de VentanaPrincipal con los
     * controladores de acción.
     */
    private void conectarEventos() {
        // Conmutación de canal analógico (HU-02 / Subtareas #12 y #15)
        vista.addListenerCanalAnalogico(e -> {
            int nuevoCanal = vista.getCanalAnalogico();
            if (nuevoCanal >= 0 && nuevoCanal != canalAnalogicoActivo) {
                this.canalAnalogicoActivo = nuevoCanal;
                this.graficaAnalogica.limpiar();
                limpiarBufferMuestras();
                System.out.println(">> Canal analógico conmutado a: " + modelo.getNombresAnalogicas()[nuevoCanal]);
            }
        });

        // Conmutación de canal digital
        vista.addListenerCanalDigital(e -> {
            int nuevoCanal = vista.getCanalDigital();
            if (nuevoCanal >= 0 && nuevoCanal != canalDigitalActivo) {
                this.canalDigitalActivo = nuevoCanal;
                this.graficaDigital.limpiar();
                System.out.println(">> Canal digital conmutado a: " + modelo.getNombresDigitales()[nuevoCanal]);
            }
        });

        // Actualización de Ts en caliente (HU-03 / Subtareas #16 y #18)
        vista.addListenerCambiarMuestreo(e -> {
            String textoEntrada = vista.getTiempoMuestreoTexto();
            actualizarPeriodo(textoEntrada);
        });

        // Exportación de datos analógicos a disco (HU-04 / Subtareas #20 y #22)
        vista.addListenerGuardarAnalogica(e -> {
            exportarDatosCanalActivo();
        });
    }

    /**
     * Sanitiza y valida la entrada con ValidadorMuestreo aplicando el nuevo
     * retardo en caliente.
     *
     * @param entradaTexto Cadena cruda introducida por el usuario en la GUI.
     * @return true si el valor fue aceptado y aplicado; false si ocurrió una
     * excepción.
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

    /**
     * Abre un JFileChooser y persiste el búfer en disco mediante la clase de
     * Brayan (EscritorArchivo).
     */
    private void exportarDatosCanalActivo() {
        if (bufferMuestrasCanalActivo.isEmpty()) {
            JOptionPane.showMessageDialog(
                    vista,
                    "No hay muestras acumuladas en memoria para exportar.",
                    "Búfer Vacío",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        JFileChooser selectorArchivos = new JFileChooser();
        selectorArchivos.setDialogTitle("Exportar Señal Analógica a Archivo");
        selectorArchivos.setFileFilter(new FileNameExtensionFilter("Archivos de texto (*.txt)", "txt"));

        int seleccion = selectorArchivos.showSaveDialog(vista);
        if (seleccion == JFileChooser.APPROVE_OPTION) {
            File archivo = selectorArchivos.getSelectedFile();
            if (!archivo.getName().toLowerCase().endsWith(".txt")) {
                archivo = new File(archivo.getAbsolutePath() + ".txt");
            }

            try {
                // Invocación a la rutina oficial codificada por Brayan (Tarea #22)
                int totalEscritas = EscritorArchivo.guardarValorVsTiempo(
                        obtenerBufferCanalActivo(),
                        archivo,
                        modelo.getNombresAnalogicas()[canalAnalogicoActivo],
                        "V"
                );

                JOptionPane.showMessageDialog(
                        vista,
                        "Archivo guardado correctamente con " + totalEscritas + " muestras:\n"
                        + archivo.getAbsolutePath(),
                        "Exportación Exitosa",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        vista,
                        "Error al escribir el archivo: " + ex.getMessage(),
                        "Error I/O",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    /**
     * Retorna una copia defensiva del búfer temporal de muestras del canal
     * activo.
     */
    public synchronized List<double[]> obtenerBufferCanalActivo() {
        synchronized (bufferMuestrasCanalActivo) {
            return new ArrayList<>(bufferMuestrasCanalActivo);
        }
    }

    public void limpiarBufferMuestras() {
        bufferMuestrasCanalActivo.clear();
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
}
