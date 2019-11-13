package actividades;

import cosas.Gomon;
import hilos.Camioneta;
import hilos.Reloj;
import hilos.Transporte;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Carrera de gomones por el río : esta actividad permite que los visitantes deciendan por el río, que se
 * encuentra rodeado de manglares, compitiendo entre ellos. Para ello es necesario llegar hasta el inicio
 * de la actividad a través de bicicletas que se prestan en un stand de bicicletas, o a través de un tren
 * interno que tiene una capacidad de 15 personas como máximo. Al llegar al inicio del recorrido cada
 * persona dispondrá de un bolso con llave, en donde podrá guardar todas las pertenencias que no quiera
 * mojar. Los bolsos están identificados con un número al igual que la llave, los bolsos serán llevados en
 * una camioneta, hasta el final del recorrido en donde podrán ser retirados por el visitante. Para bajar se
 * utilizan gomones, individuales o con capacidad para 2 personas. La cantidad de gomones de cada tipo
 * es limitada. Para habilitar una largada es necesario que haya h gomones listos para salir, no importa el
 * tipo.
 */
public class CarreraGomones implements Actividad {
    private Transporte tren;
    private Gomon[] gomones;
    private boolean abierto, camionetaFin, camionetaInicio;
    private CyclicBarrier largada;
    private Camioneta camioneta;
    private Gomon gomonGanador;
    private int enCompetencia, cantCompetidores;

    public CarreraGomones(int cantGomonesIndividuales, int cantGomonesCompartidos, int capacidadTren) {
        this.gomones = new Gomon[cantGomonesIndividuales + cantGomonesCompartidos];
        this.cantCompetidores = cantGomonesIndividuales + (cantGomonesCompartidos * 2);
        this.tren = new Transporte("Tren", capacidadTren, 1);
        this.abierto = false;
        // cuando todos dejan los bolsos en la camioneta se larga la carrera y la camineta deja los bolsos en el final
        this.largada = new CyclicBarrier(cantCompetidores);
        this.camioneta = new Camioneta(this);

        this.gomonGanador = null;
        this.camionetaInicio = true;
        this.camionetaFin = false;
        this.enCompetencia = 0;

        // creo los gomones
        for (int i = 0; i < cantGomonesIndividuales; i++) {
            gomones[i] = new Gomon(1);
        }
        for (int i = cantGomonesIndividuales; i < gomones.length; i++) {
            gomones[i] = new Gomon(2);
        }

        this.tren.start();
        this.camioneta.start();
    }

    @Override
    public synchronized void abrir() {
        tren.abrir();
        abierto = true;
        System.out.println("Carrera de gomones abierto");
    }

    @Override
    public synchronized boolean entrar() {
        return abierto;
    }

    /**
     * El competidor trata de ir al inicio de la carrera en tren y si no puede va en bicicleta
     */
    public void irAlInicio() {
        if (tren.subirse()) {
            tren.bajarse();
        } else {
            irEnBici();
        }
    }

    private void irEnBici() {
        System.out.println(Thread.currentThread().getName() + " en bicicleta...");
        Reloj.dormirHilo(5, 0);
    }

    /**
     * El competidor espera a que esté la camioneta disponible para poder dejar su bolso allí
     */
    public synchronized void dejarBolso() {
        while (!camionetaInicio) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(Thread.currentThread().getName() + " dejó su bolso");
    }

    /**
     * El competidor se sube a un gomón que esté disponible, no importa si es indiviual o compartido
     *
     * @return retorna el gomon al que se subio el visitante
     */
    public Gomon subirseAGomon() {
        int i = 0;
        while (i < gomones.length && !gomones[i].subir()) {
            i++;
        }
        // System.out.println(Thread.currentThread().getName() + " se subio al gomon " + gomones[i]);
        // TODO ver xq entran más si ya empezó la carrera, ArrayIndexOutOfBoundsException
        return gomones[i];
    }

    /**
     * Cuando esten todos o no venga nadie mas se larga la carrera y el primero en salir libera la camioneta
     * para que lleve los bolsos.
     */
    public void competir() {
        try {
            System.out.println(Thread.currentThread().getName() + " esperando para competir... ");
            largada.await(2 * Reloj.DURACION_HORA, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {
        } catch (TimeoutException e) {
            largada.reset();
        }
        synchronized (this) {
            // si es el primero en salir, la camioneta lleva los bolsos
            enCompetencia++;
            if (enCompetencia == 1) {
                camionetaInicio = false;
            }
            notifyAll();
        }
        System.out.println(Thread.currentThread().getName() + " compitiendo...");
        Reloj.dormirHilo(3, 5);
    }

    /**
     * Método utilizado por la camioneta.
     * Espera a que todos los competidores dejen los bolsos
     */
    public synchronized void esperarQueDejenBolsos() {
        System.out.println("Camioneta en inicio");
        while (camionetaInicio) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Método utilizado por la camioneta.
     * Cuando todos dejan los bolsos en la camioneta se larga la carrera y la camineta deja los bolsos en el final
     */
    public void llevarBolsos() {
        System.out.println("Camioneta llevando los bolsos...");
        Reloj.dormirHilo(2, 3);
        synchronized (this) {
            camionetaFin = true;
            notifyAll();
        }
    }

    /**
     * Si el gomón enviado por parámetro es el ganador, lo notifica.
     * Si es el último competidor en llegar a la meta resetea el gomón ganador.
     *
     * @param gomon
     */
    public synchronized void terminarCarrera(Gomon gomon) {
        // si es el primero guarda el gomon ganador
        if (gomonGanador == null) {
            gomonGanador = gomon;
        }
        System.out.println(Thread.currentThread().getName() + " llegó al final");
        if (gomonGanador == gomon) {
            System.out.println(Thread.currentThread().getName() + " Ganó");
        }
        gomon.bajarse();
        enCompetencia--;
        // si es el último en llegar resetea el gomon ganador
        if (enCompetencia == 0) {
            gomonGanador = null;
        }
        notifyAll();
    }

    /**
     * El competidor espera a que esté la camioneta en la meta para poder retirar su bolso,
     * y si es el último libera a la camioneta para que vaya al inicio de la carrera.
     */
    public synchronized void retirarBolso() {
        while (!camionetaFin) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (enCompetencia == 0) {
            camionetaFin = false;
            notifyAll();
        }
        System.out.println(Thread.currentThread().getName() + " retiró su bolso");
    }

    /**
     * Método utilizado por la camioneta.
     * Espera a que los competidores retiren sus bolsos.
     */
    public synchronized void esperarQueRetirenBolsos() {
        while (camionetaFin) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Método utilizado por la camioneta
     * Vuelve al punto de partida de la carrera para que los competidores dejen sus bolsos
     */
    public void volver() {
        System.out.println("Camioneta volviendo...");
        Reloj.dormirHilo(2, 3);
        synchronized (this) {
            // les avisa que pueden dejar sus bolsos
            camionetaInicio = true;
            notifyAll();
        }
    }

    @Override
    public synchronized void salir() {
        System.out.println(Thread.currentThread().getName() + " salió de la carrera de gomones");
    }

    @Override
    public synchronized void cerrar() {
        tren.cerrar();
        abierto = false;
    }

    @Override
    public synchronized boolean isAbierto() {
        return abierto;
    }
}


