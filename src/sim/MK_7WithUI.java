package sim;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.swing.JFrame;

import org.jfree.data.xy.XYSeries;

import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.portrayal.DrawInfo2D;
import sim.portrayal.geo.GeomPortrayal;
import sim.portrayal.geo.GeomVectorFieldPortrayal;
import sim.util.media.chart.TimeSeriesChartGenerator;
import agents.MainAgent;

/**
 * "MK_7" is the seventh iteration of my EngD project model. It is adapted from
 * the MASON demo, "Gridlock", made by Sarah Wise, Mark Coletti, and Andrew Crooks.
 * 
 * The model reads a number of GIS shapefiles and displays a road network, two
 * Environment Agency flood maps and a bespoke Open Source Vulnerability Index (OSVI).
 * The model reads in a .CSV and generates a predetermined number of agents with set
 * characteristics. The agents are placed on the road network and are located at
 * a Red Cross office. The model reads a separate .CSV and assigns goal locations to 
 * each agent at random from a predetermined list. The agents are assigned speeds at
 * random.
 * Once the model is started, the agents move from A to B, then they change direction
 * and head back to their start position. The process repeats until the user quits.
 *
 * @author KJGarbutt
 *
 */
public class MK_7WithUI extends GUIState	{

	//////////////////////////////////////////////////////////////////////////////
	/////////////////////////// DISPLAY FUNCTIONS ////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////

	private Display2D display;
    private JFrame displayFrame;

    //private GeomVectorFieldPortrayal lsoaPortrayal = new GeomVectorFieldPortrayal();
    GeomVectorFieldPortrayal polyPortrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal roadsPortrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal flood3Portrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal flood2Portrayal = new GeomVectorFieldPortrayal();
    private GeomVectorFieldPortrayal agentPortrayal = new GeomVectorFieldPortrayal();
    //SparseGridPortrayal2D agentPortrayal = new SparseGridPortrayal2D();
    TimeSeriesChartGenerator trafficChart;
    XYSeries maxSpeed;
    XYSeries avgSpeed;
    XYSeries minSpeed;

    //////////////////////////////////////////////////////////////////////////////
    /////////////////////////// BEGIN FUNCTIONS //////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////

    /**
     * ///////////////////////// Default constructor /////////////////////////////
     * Default constructor
     */
    protected MK_7WithUI(SimState state)	{
            super(state);
        }

    /**
     * //////////////////////// Portrayal Setup //////////////////////////////////
     * Sets up the portrayals and charts for the simulation
     */
    private void setupPortrayals()	{
    	sim.MK_7 world = (sim.MK_7) state;

        // the polygon portrayal
        polyPortrayal.setField(world.world);
        polyPortrayal.setPortrayalForAll(new PolyPortrayal());

        display.reset();

        display.repaint();
    }

        /**
         * ///////////////////////// Main Function ///////////////////////////////
         *
         * Main function to run the simulation
         * @param args
         */
        public static void main(String[] args)	{
        	MK_7WithUI simple = new MK_7WithUI(
        			new sim.MK_7(System.currentTimeMillis()));
            Console c = new Console(simple);
            c.setVisible(true);
        }

        /**
         * //////////////////////// Simulation Name //////////////////////////////
         * @return name of the simulation
         */
        public static String getName()	{
            return "EngD ABM Model MK_7";
        }

        /**
         *  /////////////////////// Model Modification ///////////////////////////
         *  This must be included to have model tab, which allows mid-simulation
         *  modification of the coefficients
         */
        public Object getSimulationInspectedObject()	{
            return state;
        }  // non-volatile

        /**
         * //////////////////////// Model Setup //////////////////////////////////
         * Called when starting a new run of the simulation. Sets up the portrayals
         * and chart data.
         */
        public void start()	{
            super.start();

            setupPortrayals();

            sim.MK_7 world = (sim.MK_7) state;

            maxSpeed = new XYSeries("Max Speed");
            avgSpeed = new XYSeries("Average Speed");
            minSpeed = new XYSeries("Min Speed");
            trafficChart.removeAllSeries();
            trafficChart.addSeries(maxSpeed, null);
            trafficChart.addSeries(avgSpeed, null);
            trafficChart.addSeries(minSpeed, null);

            state.schedule.scheduleRepeating(new Steppable()	{
				private static final long serialVersionUID = -3749005402522867098L;

				public void step(SimState state)	{
                	sim.MK_7 world = (sim.MK_7) state;
                    double maxS = 0, minS = 10000, avgS = 0, count = 0;
                    //////////////////////////// Main Agent //////////////////////
                    for (MainAgent a : world.agentList)	{
                        if (a.reachedGoal)	{
                            continue;
                        }
                        count++;
                        double speed = Math.abs(a.speed);
                        avgS += speed;
                        if (speed > maxS)	{
                            maxS = speed;
                        }
                        if (speed < minS)	{
                            minS = speed;
                        }
                    }

                    double time = state.schedule.time();
                    avgS /= count;
                    maxSpeed.add(time, maxS, true);
                    minSpeed.add(time, minS, true);
                    avgSpeed.add(time, avgS, true);
                }
            });

        	/**
        	 * Sets up the portrayals within the map visualization.
        	 */

            roadsPortrayal.setField(world.roads);
            roadsPortrayal.setPortrayalForAll(new GeomPortrayal(Color.DARK_GRAY, 0.0005, false));

            polyPortrayal.setField(world.world);
            polyPortrayal.setPortrayalForAll(new PolyPortrayal());

            flood3Portrayal.setField(world.flood3);
            flood3Portrayal.setPortrayalForAll(new GeomPortrayal(Color.CYAN, true));

            flood2Portrayal.setField(world.flood2);
            flood2Portrayal.setPortrayalForAll(new GeomPortrayal(Color.BLUE, true));

            agentPortrayal.setField(world.agents);
            agentPortrayal.setPortrayalForAll(new GeomPortrayal(Color.MAGENTA, 150, true));
            //agentPortrayal.setPortrayalForAll(new GeomPortrayal());

            display.reset();
            display.setBackdrop(Color.WHITE);
            display.repaint();

        }

        /**
         * /////////////////////// Poly Portrayal Colours ////////////////////////
         * The portrayal used to display Polygons with the appropriate color
         * */
        class PolyPortrayal extends GeomPortrayal
        {

            private static final long serialVersionUID = 1L;

            @Override
            public void draw(Object object, Graphics2D graphics, DrawInfo2D info)
            {
                Polygon poly = (Polygon) object;

                if (poly.getSoc().equals("red"))
                {
                    paint = Color.red;
                }

                else if (poly.getSoc().equals("orange"))
                {
                    paint = Color.orange;
                }

                else if (poly.getSoc().equals("yellow"))
                {
                    paint = Color.yellow;
                }

                else if (poly.getSoc().equals("green"))
                {
                    paint = Color.green;
                }
                else
                {
                    paint = Color.gray;
                }

                super.draw(object, graphics, info);
            }

        }

        /**
         * /////////////////////// Visualisation Format //////////////////////////
         * Initializes the simulation visualization. Sets up the display
         * window, the JFrames, and the chart structure.
         */
        public void init(Controller c)
        {
            super.init(c);

            /////////////////////////// MAIN DISPLAY /////////////////////////////
            // makes the displayer and visualises the maps
            display = new Display2D(1200, 600, this);
            // turn off clipping
            // display.setClipping(false);

            displayFrame = display.createFrame();
            displayFrame.setTitle("EngD ABM Model MK_7");
            c.registerFrame(displayFrame); // register the frame so it appears in

            // Put portrayals in order from bottom layer to top
            displayFrame.setVisible(true);
            //display.attach(polyPortrayal, "LSOA");
            //display.attach(flood2Portrayal, "FZ2 Zone");
            //display.attach(flood3Portrayal, "FZ3 Zone");
            //display.attach(roadsPortrayal, "Roads");
            display.attach(agentPortrayal, "Agents");

            ///////////////////////////// CHART //////////////////////////////////
            trafficChart = new TimeSeriesChartGenerator();
            trafficChart.setTitle("Traffic Stats");
            trafficChart.setYAxisLabel("Speed");
            trafficChart.setXAxisLabel("Time");
            JFrame chartFrame = trafficChart.createFrame(this);
            chartFrame.pack();
            c.registerFrame(chartFrame);

        }

        /**
         * /////////////////////// Model Finish //////////////////////////////////
         * Quits the simulation and cleans up.
         */
        public void quit()	{
        	System.out.println("Model closed.");
        	super.quit();

            if (displayFrame != null)	{
                displayFrame.dispose();
            }
            displayFrame = null; // let gc
            display = null; // let gc
        }
    }
