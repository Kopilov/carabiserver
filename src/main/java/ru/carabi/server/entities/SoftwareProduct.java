package ru.carabi.server.entities;

import java.io.Serializable;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * Сведения о продукте компании Караби.
 * Для хранения в базе через JPA и выдачу через SOAP.
 * @author sasha <kopilov.ad@gmail.com>
 */
@Entity
@Table(name="SOFTWARE_PRODUCTION")
@NamedQuery(name="findSoftwareProduct",
		query="SELECT P FROM SoftwareProduct P where P.sysname = :productName")
public class SoftwareProduct implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@Column(name="PRODUCTION_ID")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	
	private String name;
	private String sysname;
	private String description;
	
	@Column(name="HOME_URL")
	private String homeUrl;
	
	@Column(name="PARENT_PRODUCTION")
	private Integer parentProductId;
	
	@OneToMany(cascade=CascadeType.ALL, mappedBy="carabiProduct")
	private List<ProductVersion> versions;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		hash += (id != null ? id.hashCode() : 0);
		return hash;
	}
	
	@Override
	public boolean equals(Object object) {
		// TODO: Warning - this method won't work in the case the id fields are not set
		if (!(object instanceof SoftwareProduct)) {
			return false;
		}
		SoftwareProduct other = (SoftwareProduct) object;
		if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "ru.carabi.server.CarabiProduct[ id=" + id + " ]";
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getSysname() {
		return sysname;
	}
	
	public void setSysname(String sysname) {
		this.sysname = sysname;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public Integer getParentProductId() {
		return parentProductId;
	}
	
	public void setParentProductId(Integer parentProductId) {
		this.parentProductId = parentProductId;
	}
	
	public String getHomeUrl() {
		return homeUrl;
	}
	
	public void setHomeUrl(String homeUrl) {
		this.homeUrl = homeUrl;
	}
	
	public List<ProductVersion> getVersions() {
		return versions;
	}
	
	public void setVersions(List<ProductVersion> versions) {
		this.versions = versions;
	}
}