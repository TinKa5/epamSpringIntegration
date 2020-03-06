package epam.ua;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.scheduling.PollerMetadata;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootApplication
public class DeliveryApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(DeliveryApplication.class);

        DeliveryService ds = ctx.getBean(DeliveryService.class);

        for (int i = 1; i <= 100; i = i + 2) {
            Package p = new Package(i, OptionDelivery.DTH, 5);
            ds.putPackage(p);
            p = new Package(i + 1, OptionDelivery.DTS, 100);
            ds.putPackage(p);

        }
    }

    @MessagingGateway
    public interface DeliveryService{
        @Gateway(requestChannel = "packages.input")
        public void putPackage(Package p);

    }

    @Bean(name = PollerMetadata.DEFAULT_POLLER)
    public PollerMetadata poller() {
        return Pollers.fixedDelay(1000).get();
    }

    @Bean
    IntegrationFlow packages(){
        return f->f.channel(c->c.executor(Executors.newCachedThreadPool()))
                .log(LoggingHandler.Level.INFO)
                .<Package,OptionDelivery>route(c->c.getOption(), mapping->mapping
                        .subFlowMapping(OptionDelivery.DTH, sf->sf.channel(c->c.queue(10))
                                .publishSubscribeChannel(c->c
                                        .subscribe(s -> s.handle(m -> sleepUninterruptibly(1, TimeUnit.SECONDS)))

                                        .subscribe(s->s.<Package,String>transform(p->"Package id="+p.id
                                                +"  delivered at home. Current thread - "+Thread.currentThread().getName())
                                                .handle(m->System.out.println(m.getPayload()))))
                                .bridge()
                                .channel("storage"))


                        .subFlowMapping(OptionDelivery.DTH, sf->sf.channel(c->c.queue(10))
                                .publishSubscribeChannel(c->c
                                        .subscribe(s -> s.handle(m -> sleepUninterruptibly(1, TimeUnit.SECONDS)))
                                        .subscribe(s->s.<Package,String>transform(p->"Package id="+p.id
                                                +"  prepare to deliver at home. Current thread - "+Thread.currentThread().getName())
                                                .handle(m->System.out.println(m.getPayload()))))

                                .bridge()
                                .aggregate(agr->agr
                                        .outputProcessor(g ->
                                                new StorageDelivery(g.getMessages()
                                                        .stream()
                                                        .map(message -> (Package) message.getPayload())
                                                        .collect(Collectors.toList())))
                                        .correlationStrategy(m->1)
                                        .expireGroupsUponCompletion(true)
                                        .releaseStrategy(group->group.getMessages().size()>10))
                                .<StorageDelivery,String>transform(s->"StorageDelivery with count packages is "+s.packageList.size())
                                .handle(m->System.out.println(m.getPayload()))));




    }



    private static void sleepUninterruptibly(long sleepFor, TimeUnit unit) {
        boolean interrupted = false;
        try {
            unit.sleep(sleepFor);
        }
        catch (InterruptedException e) {
            interrupted = true;
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }




}
