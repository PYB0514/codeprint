package application;

public interface FooJpaRepository extends JpaRepository<Foo, java.util.UUID> {
    java.util.Optional<Foo> findByName(String name);
}
