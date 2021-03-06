package de.mpicbg.knime.scripting.r.port;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.zip.ZipEntry;

import javax.swing.JComponent;

import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortObjectSpecZipInputStream;
import org.knime.core.node.port.PortObjectSpecZipOutputStream;

/**
 * This class provides meta information passed by R-Ports
 * 
 * @author Antje Janosch
 *
 */
public class RPortObjectSpec2 implements PortObjectSpec {
	
	public static final RPortObjectSpec2 INSTANCE = new RPortObjectSpec2();

	/** names of all R objects provided by the R workspace and their R-type */
	private HashMap<String, String> m_rObjects;

	/**
	 * @param rObjects
	 */
	public RPortObjectSpec2(HashMap<String, String> rObjects) {
		this.m_rObjects = rObjects;
	}

	/**
	 * constructor
	 */
	public RPortObjectSpec2() {
		this.m_rObjects = new HashMap<String, String>();
	}

	/**
	 * view provided at configuration state?
	 * {@inheritDoc}
	 */
	@Override
	public JComponent[] getViews() {
		return new JComponent[]{};
	}

	/**
	 * @return a hash map with the names of R objects and their R-type
	 */
	protected HashMap<String, String> getRObjects() {
		return m_rObjects;
	}
	
	public static final class SpecSerializer extends PortObjectSpecSerializer<RPortObjectSpec2> {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void savePortObjectSpec(RPortObjectSpec2 portObjectSpec, PortObjectSpecZipOutputStream out)
				throws IOException {
			out.putNextEntry(new ZipEntry("content.dat"));
            new ObjectOutputStream(out).writeObject(portObjectSpec
                    .getRObjects());
			
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public RPortObjectSpec2 loadPortObjectSpec(PortObjectSpecZipInputStream in) throws IOException {
			in.getNextEntry();
			final ObjectInputStream ois = new ObjectInputStream(in);
            try {
                @SuppressWarnings("unchecked")
				final HashMap<String,String> rObjects = (HashMap<String,String>)ois.readObject();
                return new RPortObjectSpec2(rObjects);
            } catch (final ClassNotFoundException e) {
                throw new IOException(e.getMessage(), e.getCause());
            }
		}
	
	}

}
