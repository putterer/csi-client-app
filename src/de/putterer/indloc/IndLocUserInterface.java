package de.putterer.indloc;

import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.dataprocessing.TargetRecordingAnalyzer;
import de.putterer.indloc.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


/**
 * A user interface for Indoor Localization
 */
@SuppressWarnings("serial")
public class IndLocUserInterface extends JPanel implements KeyListener {

	public static boolean DEMO_MODE = true;
	public static List<de.putterer.indloc.util.Vector> measuredLocations = Collections.emptyList();
	// ---------------------------------------------------


	private static final int RENDER_STATION_SIZE = 30;
	private static final int RENDER_LOCATION_SIZE = 24;
	private static final int RENDER_TARGET_SIZE = 24;
	private float RENDER_SCALE = 1.00f;
//	private float RENDER_SCALE = 0.58f;
	
	private boolean renderGrid = true;
	private Config.Target target = null;
	private FileWriter targetRecordingWriter;
	
	private final JFrame frame;

	private final Config.RoomConfig room;
	private List<de.putterer.indloc.util.Vector> trilaterationLocations = new LinkedList<>();

	private Image scaledBackground;
	
	public IndLocUserInterface(Config.RoomConfig room) {
		this.room = room;
		Arrays.stream(room.getStations()).forEach(s -> s.rssiProperty().addListener(() -> this.repaint()));
		
		frame = new JFrame("Indoor Localization - Fabian Putterer - TUM");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setScale(RENDER_SCALE);
		frame.setLocationRelativeTo(null);
		
		frame.addKeyListener(this);
		
		frame.add(this);
		
		frame.setVisible(true);
	}

	@Override
	protected void paintComponent(Graphics _void) {
		Graphics2D g = (Graphics2D) _void;
		g.clearRect(0, 0, this.getWidth(), this.getHeight());

		//Background image
		if(scaledBackground != null) {
			g.drawImage(scaledBackground, 0, 0, null);
		}

		//Grid
		g.setStroke(new BasicStroke(2));
		g.setColor(Color.LIGHT_GRAY);
		for(int x = 0;x < room.getWidth();x+=100) {
			g.drawLine(s(x), 0, s(x), s(room.getHeight()));
		}
		for(int y = 0;y < room.getHeight();y+=100) {
			g.drawLine(0, s(y), s(room.getWidth()), s(y));
		}
		g.setStroke(new BasicStroke(1));
		
		g.setColor(new Color(100, 50, 0));
//		renderFontView(Config.RSSI_DISTANCE_ESTIMATOR.toString(), 5, getHeight() - 5, g);
		
		//Stations
		g.setColor(new Color(219, 33, 0));
		for(de.putterer.indloc.util.Vector l : Arrays.stream(room.getStations()).map(Station::getLocation).collect(Collectors.toList())) {
			g.fillOval(s(l.getX() - RENDER_STATION_SIZE / 2), s(l.getY() - RENDER_STATION_SIZE / 2), s(RENDER_STATION_SIZE), s(RENDER_STATION_SIZE));
		}
		
		//RSSI
		g.setColor(new Color(30, 210, 0));
		for(Station s : room.getStations()) {
			int radius = s.getRSSI().toDistance();
			g.drawOval(s(s.getLocation().getX() - radius), s(s.getLocation().getY() - radius), s(radius * 2), s(radius * 2));
		}
		
		g.setColor(new Color(50, 50, 100));
		g.setFont(new Font("Ubuntu", Font.BOLD, 28));
		for(Station s : room.getStations()) {//TODO: REFACTOR
			int side = s.getLocation().getX() > getWidth() / 3 * 2 ? -4 : 1;
			if(! DEMO_MODE) {
				renderFontWorld(s.getIP_ADDRESS(), s.getLocation().getX() + 20 * side, s.getLocation().getY() + 22, g);
				renderFontWorld(s.getHW_ADDRESS(), s.getLocation().getX() + 20 * side, s.getLocation().getY() + 42, g);
				renderFontWorld(String.valueOf((int)s.getRSSI().getRssi()) + " dBm", s.getLocation().getX() + 20 * side, s.getLocation().getY() + 62, g);
			} else {
				renderFontWorld(s.getIP_ADDRESS().split("\\.")[3], s.getLocation().getX() + 20, s.getLocation().getY() + 22, g);
			}
		}

		//Target
		Arrays.stream(room.getObjects())
				.filter(o -> o instanceof Config.Target)
				.map(o -> (Config.Target)o)
				.forEach(t -> {
					g.setColor(new Color(255, 190,0));
					if(t == target) {
						g.setColor(new Color(142, 255,0));
					}
					g.fillOval(s(t.getPosition().getX() - RENDER_TARGET_SIZE / 2), s(t.getPosition().getY() - RENDER_TARGET_SIZE / 2), s(RENDER_TARGET_SIZE), s(RENDER_TARGET_SIZE));
					if(DEMO_MODE) {
						g.setColor(new Color(198, 122, 15));
					}
					renderFontWorld(String.valueOf(t.getId()), t.getPosition().getX() + 7, t.getPosition().getY() + 32, g);
				});


		if(DEMO_MODE) {
			for(de.putterer.indloc.util.Vector loc : measuredLocations) {
				g.setColor(new Color(0, 30, 200));
				g.fillOval(s(loc.getX() - RENDER_TARGET_SIZE / 2), s(loc.getY() - RENDER_TARGET_SIZE / 2), s(RENDER_TARGET_SIZE), s(RENDER_TARGET_SIZE));
				renderFontWorld(String.valueOf(measuredLocations.indexOf(loc) + 1), loc.getX() + 7, loc.getY() + 32, g);
			}
		}
		
		if(! DEMO_MODE) {
			de.putterer.indloc.util.Vector estimatedLocation = Config.TRILATERATOR.estimate(Arrays.asList(room.getStations()));

			g.setColor(new Color(100, 100, 100));
			if(estimatedLocation != null) {
				g.fillOval(s(estimatedLocation.getX() - RENDER_LOCATION_SIZE / 4), s(estimatedLocation.getY() - RENDER_LOCATION_SIZE / 4), RENDER_LOCATION_SIZE / 2, RENDER_LOCATION_SIZE / 2);
			}

			trilaterationLocations.add(estimatedLocation);
			while(trilaterationLocations.size() > Config.MAX_LOCATION_PAST_LENGTH) {
				trilaterationLocations.remove(0);
			}
			estimatedLocation = getSmoothedEstimation();

			g.setColor(new Color(0, 30, 200));
			g.fillOval(s(estimatedLocation.getX() - RENDER_LOCATION_SIZE / 2), s(estimatedLocation.getY() - RENDER_LOCATION_SIZE / 2), RENDER_LOCATION_SIZE, RENDER_LOCATION_SIZE);
		}
	}

	private de.putterer.indloc.util.Vector getSmoothedEstimation() {
		return trilaterationLocations.stream().filter(Objects::nonNull).reduce(new de.putterer.indloc.util.Vector(), (l, r) -> l.add(r)).scale(1f / trilaterationLocations.size());
	}

	private void renderFontWorld(String txt, float x, float y, Graphics2D g) {
		renderFontView(txt, s(x), s(y), g);
	}
	
	private void renderFontView(String txt, float x, float y, Graphics2D g) {
		g.drawChars(txt.toCharArray(), 0, txt.length(), (int)x, (int)y);
	}
	
	/**
	 * scales the value linearly using RENDER_SCALE as a factor
	 */
	private int s(int c) {
		return s((float)c);
	}
	
	private int s(float c) {
		return (int)(c * RENDER_SCALE);
	}
	
	private void setScale(float scale) {
		this.RENDER_SCALE = scale;
		Arrays.stream(room.getObjects())
				.filter(o -> o instanceof Config.Background)
				.findFirst()
				.ifPresent(o -> {
					BufferedImage image = ((Config.Background) o).getImage();
					scaledBackground = image.getScaledInstance(s(image.getWidth()), s(image.getHeight()), Image.SCALE_SMOOTH);
				});
		frame.setSize(s(room.getWidth()), s(room.getHeight()));
		this.repaint();
	}

	public void renderImage(BufferedImage image) {
		paintComponent(image.createGraphics());
	}

	@Override
	public void keyPressed(KeyEvent e) {
		switch(e.getKeyCode()) {
		case KeyEvent.VK_ESCAPE: System.exit(0); break;
		case KeyEvent.VK_G: renderGrid = !renderGrid; this.repaint(); break;
		case KeyEvent.VK_PLUS: setScale(RENDER_SCALE * 1.10f);break;
		case KeyEvent.VK_MINUS: setScale(RENDER_SCALE * 1 / 1.10f);break;
		case KeyEvent.VK_R: {
			if(target == null) {
				target = Arrays.stream(room.getObjects())
						.filter(o -> o instanceof Config.Target)
						.map(o -> (Config.Target)o)
						.filter(t -> t.getId() == 1)
						.findFirst().orElse(null);

				try {
					targetRecordingWriter = new FileWriter(Paths.get("./targetRecording.txt").toFile());
				} catch (IOException ex) {
					ex.printStackTrace();
					Logger.error("Error while creating target recording file writer.");
				}
				Logger.info("Starting target recording");
			} else {
				Vector estimatedLocation = getSmoothedEstimation();
				String data = String.format("Target: %d,%d  Estimation: %d,%d  Distance: %f",
						(int)target.getPosition().getX(),
						(int)target.getPosition().getY(),
						(int)estimatedLocation.getX(),
						(int)estimatedLocation.getY(),
						target.getPosition().sub(estimatedLocation).length());
				Logger.info(data);
				try {
					targetRecordingWriter.write(data + "\n");
					targetRecordingWriter.flush();
				} catch (IOException ex) {
					Logger.error("Error while writing to target recording log.");
					ex.printStackTrace();
				}
				target = Arrays.stream(room.getObjects())
						.filter(o -> o instanceof Config.Target)
						.map(o -> (Config.Target)o)
						.filter(t -> t.getId() == target.getId() + 1)
						.findFirst().orElse(null);
				if(target == null) {
					try {
						targetRecordingWriter.flush();
						targetRecordingWriter.close();
					} catch (IOException ex) {
						Logger.error("Error while closing target recording log");
						ex.printStackTrace();
					}
					Logger.info("Target recording stopped");
				}
			}

			break;
		}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void keyTyped(KeyEvent e) {}
	
	public static void main(String args[]) throws IOException {
		measuredLocations = TargetRecordingAnalyzer.getMeasuredLocations();

		IndLocUserInterface ui = new IndLocUserInterface(Config.ROOM);

		BufferedImage image = new BufferedImage((int) (1072 * 1.00f), (int) (1258 * 1.00f), BufferedImage.TYPE_INT_ARGB);
		ui.renderImage(image);
		ImageIO.write(image, "PNG", Paths.get("renderedImage.png").toFile());

		System.exit(0);
	}
}
