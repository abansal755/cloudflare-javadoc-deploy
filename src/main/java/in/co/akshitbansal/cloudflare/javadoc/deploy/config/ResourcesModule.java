package in.co.akshitbansal.cloudflare.javadoc.deploy.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public class ResourcesModule extends AbstractModule {

    private final ExecutorService executor;

    @Provides
    @Singleton
    public ExecutorService provideExecutor() {
        return executor;
    }
}
