package domain.user;

import javax.persistence.Entity;
import javax.persistence.Convert;

@Entity
public class User {
    @Convert(converter = EmailConverter.class)
    private String email;
}
