/******************************************************************************
 *                                                                            *
 * Copyright 2018 Jan Henrik Weinstock                                        *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *     http://www.apache.org/licenses/LICENSE-2.0                             *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 *                                                                            *
 ******************************************************************************/

package org.vcml.session;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Path;

public class Session {
	
	public final static String ANNOUNCE_DIR = System.getProperty("java.io.tmpdir");
	
	private String uri = "";
	
	private String host = "";
	
	private int port = 0;
	
	private String exec = "<unknown>";
	
	private String user = "<unknown>";
	
	private String name = "<unknown>";
	
	private RemoteSerialProtocol protocol = null;
	
	private Module hierarchy = null;
	
	private double simTime = 0.0;
	
	private boolean running = false;
	
	public String getURI() {
		return uri;
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
	
	public String getUser() {
		return user;
	}
	
	public String getName() {
		return name;
	}
	
	public String getExecutable() {
		return exec;
	}
	
	public double getTime() {
		return simTime;
	}
	
	public boolean isConnected() {
		return protocol != null;
	}
	
	public boolean isRunning() {
		return running;
	}

	@Override
	public String toString() {
		return user + "/" + name + " at " + host + ":" + port;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		
		if (!(other instanceof Session))
			return false;
		
		Session session = (Session)other;
		return uri.equals(session.getURI());
	}
	
	private void updateTime() throws SessionException {
		Response r = protocol.command(RemoteSerialProtocol.TIME);
		simTime = Double.parseDouble(r.toString());
	} 
	
	public Session(String uri) throws SessionException {
		this.uri = uri;

		String[] info = uri.split(":");
		if (info.length >= 2) {
			host = info[0];
			port = Integer.parseInt(info[1]);
			if (info.length > 2)
				user = info[2];
			if (info.length > 3) {
				exec = info[3];
				
				Path path = new Path(exec);
				name = path.segment(path.segmentCount() - 1);
			}
		}
		
		if (host.isEmpty() || port == 0)
			throw new SessionException("invalid URI: " + uri);
	}
	
	public void connect() throws SessionException {
		if (isConnected())
			return;

		protocol = new RemoteSerialProtocol(host, port);
		updateTime();
	}
	
	public void disconnect() throws SessionException {
		if (!isConnected())
			return;

		hierarchy = null;
		protocol.close();
		protocol = null;
	}

	public Module[] getTopLevelObjects() throws SessionException {
		if (!isConnected() || isRunning())
			return null;
		
		if (hierarchy == null)
			hierarchy = new Module(protocol, null, "");
		return hierarchy.getChildren();
	}
	
	public Module findObject(String name) throws SessionException {
		if (hierarchy == null)
			hierarchy = new Module(protocol, null, "");
		return hierarchy.findChild(name);
	}
	
	public void continueSimulation() throws SessionException {
		if (!isConnected() || isRunning())
			return;

		protocol.send(RemoteSerialProtocol.CONT);
		running = true;
		hierarchy = null; // needs to be rebuild
	}
	
	public void stopSimulation() throws SessionException {
		if (!isConnected() || !isRunning())
			return;

		protocol.send_char('a');
		String resp = protocol.recv();
		if (!resp.equals("OK"))
			throw new SessionException("Simulator responded with error : " + resp);
		running = false;
		
		updateTime();
	}
	
	public void stepSimulation() throws SessionException {
		if (!isConnected() || isRunning())
			return;

		protocol.command(RemoteSerialProtocol.STEP);
		hierarchy = null; // needs to be rebuild
		
		updateTime();
	}
	
	public void quitSimulation() throws SessionException {
		if (!isConnected())
			return;

		protocol.send(RemoteSerialProtocol.QUIT);
		running = false;
	}
	
	public static List<Session> getAvailableSessions() {
		List<Session> avail = new ArrayList<Session>();
		
		File directory = new File(ANNOUNCE_DIR);
		File[] files = directory.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return Pattern.matches("vcml_session_[0-9]+", name);
			}
		});
		
		for (File it : files) {
			try {
				Scanner scanner = new Scanner(it);
				try {
					String uri = scanner.nextLine();
					Session session = new Session(uri);
					if (!avail.contains(session))
						avail.add(session);
				} catch (SessionException ex) {
					System.err.println(ex.getMessage());
				} finally {
					scanner.close();
				}
			} catch (FileNotFoundException e) {
				/* nothing to do */
			}
		}
		
		return avail;
	}
}
