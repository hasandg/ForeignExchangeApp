package com.hasandag.exchange.conversion.config;

import com.hasandag.exchange.common.dto.ConversionRequest;
import com.hasandag.exchange.common.dto.ConversionResponse;
import com.hasandag.exchange.conversion.batch.ConversionItemProcessor;
import com.hasandag.exchange.conversion.batch.ConversionItemWriter;
import com.hasandag.exchange.conversion.batch.CsvConversionItemReader;
import com.hasandag.exchange.conversion.batch.CurrencyConversionMongoItemWriter;
import com.hasandag.exchange.conversion.batch.CurrencyConversionPostgresItemWriter;
import com.hasandag.exchange.conversion.client.ExchangeRateFeignClient;
import com.hasandag.exchange.conversion.repository.command.CurrencyConversionMongoRepository;
import com.hasandag.exchange.conversion.repository.query.CurrencyConversionPostgresRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableAsync
@Slf4j
@RequiredArgsConstructor
public class BatchConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ExchangeRateFeignClient exchangeRateFeignClient;
    private final CurrencyConversionMongoRepository mongoRepository;
    private final CurrencyConversionPostgresRepository postgresRepository;

    @Bean
    public ItemReader<ConversionRequest> csvItemReader() {
        return new CsvConversionItemReader();
    }

    @Bean
    public ItemProcessor<ConversionRequest, ConversionResponse> batchConversionItemProcessor() {
        return new ConversionItemProcessor(exchangeRateFeignClient, postgresRepository, mongoRepository);
    }

    private CurrencyConversionPostgresItemWriter createPostgresItemWriter() {
        return new CurrencyConversionPostgresItemWriter(postgresRepository);
    }

    private CurrencyConversionMongoItemWriter createMongoItemWriter() {
        return new CurrencyConversionMongoItemWriter(mongoRepository);
    }

    @Bean
    public ItemWriter<ConversionResponse> batchConversionItemWriter() {
        List<ItemWriter<? super ConversionResponse>> delegates = new ArrayList<>();

        delegates.add(createMongoItemWriter());
        delegates.add(createPostgresItemWriter());

        return new ConversionItemWriter(delegates);
    }

    @Bean
    public Step conversionCsvProcessingStep(
            ItemReader<ConversionRequest> reader,
            ItemProcessor<ConversionRequest, ConversionResponse> processor,
            ItemWriter<ConversionResponse> writer,
            @Value("${conversion.batch.chunk-size:100}") int chunkSize,
            @Value("${conversion.batch.skip-limit:1000}") int skipLimit,
            @Value("${conversion.batch.retry-limit:3}") int retryLimit) {

        log.warn("CONFIGURING STEP with chunkSize={}, skipLimit={}, retryLimit={}", chunkSize, skipLimit, retryLimit);

        return new StepBuilder("conversionCsvProcessingStep", jobRepository)
                .<ConversionRequest, ConversionResponse>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(IllegalArgumentException.class)
                .skip(java.util.NoSuchElementException.class)
                .retryLimit(retryLimit)
                .retry(FeignException.class)
                .retry(HttpStatusCodeException.class)
                .noRetry(OptimisticLockingFailureException.class)
                .build();
    }

    @Bean
    public Job bulkConversionJob(Step conversionCsvProcessingStep) {
        log.info("Configuring bulkConversionJob");
        return new JobBuilder("bulkConversionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .flow(conversionCsvProcessingStep)
                .end()
                .build();
    }

    @Bean
    @Qualifier("asyncJobLauncher")
    @Primary
    public JobLauncher asyncJobLauncher() throws Exception {
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(batchTaskExecutor());

        jobLauncher.afterPropertiesSet();

        log.warn("✅ Configured ASYNC JobLauncher as PRIMARY - jobs will return immediately");
        log.warn("✅ Using TaskExecutor: {} with core pool size: 2", batchTaskExecutor().getClass().getSimpleName());

        return jobLauncher;
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("batch-async-");

        executor.setDaemon(false);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.warn("✅ Configured TaskExecutor - core: 2, max: 4, queue: 100, daemon: false");
        return executor;
    }
} 