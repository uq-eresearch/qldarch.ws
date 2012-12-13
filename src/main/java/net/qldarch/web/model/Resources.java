package net.qldarch.web.model;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "field1"
})
@XmlRootElement(name = "resources", namespace = "http://qldarch.net/ns/xml")
public class Resources {

    @XmlElement(name = "resource", required = true)
    protected List<Resource> field1;

    /**
     * Gets the value of the field1 property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the field1 property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getfield1().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Resource }
     * 
     * 
     */
    public List<Resource> getfield1() {
        if (field1 == null) {
            field1 = new ArrayList<Resource>();
        }
        return this.field1;
    }

}
