package eu.arrowhead.core.authorization.database;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@Entity
@Table(uniqueConstraints={@UniqueConstraint(columnNames = {"systemGroup", "systemName"})})
@XmlRootElement
public class ArrowheadSystem {
	
	@Id @GeneratedValue(strategy=GenerationType.AUTO)
	@XmlTransient
	private int id;
	private String systemGroup;
	private String systemName;
	private String IPAddress;
	private String port;
	private String authenticationInfo;
	
	public ArrowheadSystem(){
		
	}
	
	public ArrowheadSystem(String systemGroup, String systemName, 
			String iPAddress, String port, String authenticationInfo) {
		super();
		this.systemGroup = systemGroup;
		this.systemName = systemName;
		this.IPAddress = iPAddress;
		this.port = port;
		this.authenticationInfo = authenticationInfo;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getSystemGroup() {
		return systemGroup;
	}

	public void setSystemGroup(String systemGroup) {
		this.systemGroup = systemGroup;
	}

	public String getSystemName() {
		return systemName;
	}

	public void setSystemName(String systemName) {
		this.systemName = systemName;
	}

	public String getIPAddress() {
		return IPAddress;
	}

	public void setIPAddress(String iPAddress) {
		IPAddress = iPAddress;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getAuthenticationInfo() {
		return authenticationInfo;
	}

	public void setAuthenticationInfo(String authenticationInfo) {
		this.authenticationInfo = authenticationInfo;
	}
	
	
}