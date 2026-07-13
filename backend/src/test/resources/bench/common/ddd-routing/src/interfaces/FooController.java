package interfaces;

import infrastructure.persistence.FooRepositoryImpl;

public class FooController {
    public void handle() {
        FooRepositoryImpl repo = new FooRepositoryImpl();
        repo.save();
    }
}
