package net.kaikk.mc.synx;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.kaikk.mc.synx.packets.Node;

class DataStore {
	private SynX instance;
	private Connection db;
	
	ExecutorService executor = Executors.newSingleThreadExecutor();
	
	DataStore(SynX instance) throws Exception {
		this.instance=instance;
		
		try {
			this.dbCheck();
		} catch(Exception e) {
			this.instance.log("ERROR: Unable to connect to database. Check your config file settings. Details: \n"+e.getMessage());
			throw e;
		}
		
		Statement statement = db.createStatement();

		try {
			// Creates tables on the database
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS synx_data (id bigint(20) unsigned NOT NULL AUTO_INCREMENT,req int(11) NOT NULL,channel char(16) NOT NULL,tod bigint(20) NOT NULL,dat varbinary(32727) NOT NULL,PRIMARY KEY (id),KEY channel (channel));");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS synx_servers (id int(11) NOT NULL AUTO_INCREMENT,name char(8) NOT NULL,lastaction bigint(20) NOT NULL,tags varchar(255) NOT NULL,PRIMARY KEY (id),UNIQUE KEY name (name));");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS synx_transfers (id bigint(20) NOT NULL AUTO_INCREMENT,dest int(11) NOT NULL,dataid bigint(20) unsigned NOT NULL,PRIMARY KEY (id),KEY dest (dest));");
		} catch(Exception e) {
			this.instance.log("ERROR: Unable to create the necessary database table. Details: \n"+e.getMessage());
			throw e;
		}
		
		// Load nodes and tags
		PreparedStatement ps = this.prepareStatement("SELECT * FROM synx_servers WHERE lastaction > ?");
		ps.setLong(1, System.currentTimeMillis()-86400000);
		ResultSet rs = ps.executeQuery();
		List<Node> nodesToBeAdded = new ArrayList<Node>();
		while (rs.next()) {
			nodesToBeAdded.add(new Node(rs.getInt(1), rs.getString(2), rs.getString(4).split(",")));
		}
		
		Iterator<Node> it = nodesToBeAdded.iterator();
		while (it.hasNext()) {
			Node n = it.next();
			if (n.getName().equals(instance.config().nodeName)) {
				if (!Utils.compareCollections(Arrays.asList(n.getTags()), instance.config().tags)) {
					it.remove(); // database data needs update!
					instance.log("Node data needs to be updated!");
				} else {
					instance.node = n;
				}
				break;
			}
		}
		
		for (Node n : nodesToBeAdded) {
			instance.addNode(n);
		}

		if (instance.node == null) {
			// Initialize node data
			instance.log("Initializing node data");
			ps = this.prepareStatement("INSERT INTO synx_servers (name, lastaction, tags) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE lastaction = VALUES(lastaction), tags = VALUES(tags)", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, instance.config().nodeName);
			ps.setLong(2, System.currentTimeMillis());
			ps.setString(3, String.join(",", instance.config().tags));
			ps.executeUpdate();
			rs = ps.getGeneratedKeys();
			rs.next();
			int id = rs.getInt(1);
			
			instance.node = new Node(id, instance.config().nodeName, instance.config().tags.toArray(new String[instance.config().tags.size()]));
			instance.addNode(instance.node);
		}
		
		instance.log("Loaded "+instance.nodes.size()+" nodes");
	}

	Statement statement() throws SQLException {
		this.dbCheck();
		return this.db.createStatement();
	}
	
	PreparedStatement prepareStatement(String sql) throws SQLException {
		this.dbCheck();
		return this.db.prepareStatement(sql);
	}
	
	PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		this.dbCheck();
		return this.db.prepareStatement(sql, autoGeneratedKeys);
	}
	
	void dbCheck() throws SQLException {
		if(this.db == null || this.db.isClosed()) {
			this.db = instance.getImplementation().getDataSource(instance.config().dbHostname, instance.config().dbUsername, instance.config().dbPassword, instance.config().dbDatabase).getConnection();
		}
	}
	
	void dbClose()  {
		try {
			if (!this.db.isClosed()) {
				this.db.close();
				this.db=null;
			}
		} catch (SQLException e) {
			
		}
	}
}
