package epam.ua;

import java.util.Date;

public class Package {
    Integer id;
    Date date;
    StatusDelivery status;
    OptionDelivery option;
    Integer weight;

    public Package(Integer id, OptionDelivery option, Integer weight) {
        this.id = id;
        date=new Date();
        status=StatusDelivery.NEW;
        this.option = option;
        this.weight = weight;
    }

    public OptionDelivery getOption() {
        return option;
    }
    public void setStatus(StatusDelivery status) {
        this.status = status;
    }

    public Integer getWeight() {
        return weight;
    }
}
