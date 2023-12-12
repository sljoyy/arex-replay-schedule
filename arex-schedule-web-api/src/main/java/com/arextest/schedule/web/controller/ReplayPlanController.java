package com.arextest.schedule.web.controller;

import com.arextest.schedule.common.CommonConstant;
import com.arextest.schedule.comparer.InvalidReplayCaseService;
import com.arextest.schedule.exceptions.PlanRunningException;
import com.arextest.schedule.mdc.MDCTracer;
import com.arextest.schedule.model.CaseSourceEnvType;
import com.arextest.schedule.model.CommonResponse;
import com.arextest.schedule.model.DebugRequestItem;
import com.arextest.schedule.model.plan.BuildReplayFailReasonEnum;
import com.arextest.schedule.model.plan.BuildReplayPlanRequest;
import com.arextest.schedule.model.plan.BuildReplayPlanResponse;
import com.arextest.schedule.model.plan.ReRunReplayPlanRequest;
import com.arextest.schedule.progress.ProgressEvent;
import com.arextest.schedule.progress.ProgressTracer;
import com.arextest.schedule.sender.ReplaySendResult;
import com.arextest.schedule.service.DebugRequestService;
import com.arextest.schedule.service.PlanProduceService;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author jmo
 * @since 2021/9/22
 */
@Slf4j
@Controller
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
public class ReplayPlanController {

  @Resource
  private PlanProduceService planProduceService;
  @Resource
  private ProgressTracer progressTracer;
  @Resource
  private ProgressEvent progressEvent;
  @Resource
  private DebugRequestService debugRequestService;
  @Resource
  private InvalidReplayCaseService invalidReplayCaseService;
  @PostMapping(value = "/api/createPlan")
  @ResponseBody
  public CommonResponse createPlanPost(@RequestBody BuildReplayPlanRequest request) {
    return createPlan(request);
  }

  @GetMapping(value = "/api/createPlan")
  @ResponseBody
  public CommonResponse createPlanGet(@RequestParam(name = "appId", required = true) String appId,
      @RequestParam(name = "targetEnv", required = true) String targetEnv) {
    BuildReplayPlanRequest req = new BuildReplayPlanRequest();
    req.setAppId(appId);
    req.setTargetEnv(targetEnv);

    req.setReplayPlanType(0);
    req.setOperator("Webhook");
    req.setCaseSourceFrom(new Date(System.currentTimeMillis() - CommonConstant.ONE_DAY_MILLIS));
    req.setCaseSourceTo(new Date());
    return createPlan(req);
  }

  @PostMapping("/api/reRunPlan")
  @ResponseBody
  public CommonResponse reRunPlan(@RequestBody ReRunReplayPlanRequest request) {
    try {
      return planProduceService.reRunPlan(request);
    } catch (PlanRunningException e) {
      return CommonResponse.badResponse(e.getMessage(),
          new BuildReplayPlanResponse(e.getCode()));
    } catch (Throwable e) {
      return CommonResponse.badResponse("create plan error！" + e.getMessage(),
          new BuildReplayPlanResponse(BuildReplayFailReasonEnum.UNKNOWN));
    }
  }

  @GetMapping("/api/stopPlan")
  @ResponseBody
  public CommonResponse stopPlan(String planId) {
    planProduceService.stopPlan(planId);
    return CommonResponse.successResponse("success", null);
  }

  @GetMapping("/progress")
  @ResponseBody
  public CommonResponse progress(String planId) {
    ProgressStatus progressStatus = new ProgressStatus();
    double percent = progressTracer.finishPercent(planId);
    long updateTime = progressTracer.lastUpdateTime(planId);
    progressStatus.setPercent(percent);
    progressStatus.setLastUpdateTime(new Date(updateTime));
    return CommonResponse.successResponse("ok", progressStatus);
  }

  @PostMapping("/api/debugRequest")
  @ResponseBody
  public ReplaySendResult debugRequest(@RequestBody DebugRequestItem requestItem) {
    if (requestItem == null) {
      return ReplaySendResult.failed("param is null");
    }
    if (StringUtils.isBlank(requestItem.getOperation())) {
      return ReplaySendResult.failed("operation is null or empty");
    }
    if (StringUtils.isBlank(requestItem.getMessage())) {
      return ReplaySendResult.failed("message is null or empty");
    }
    return debugRequestService.debugRequest(requestItem);
  }


  @GetMapping("/api/invalidReplayCase/{replayId}")
  @ResponseBody
  public CommonResponse invalidReplayCase(@PathVariable String replayId) {
    if (StringUtils.isEmpty(replayId)) {
      return CommonResponse.badResponse("replayId is empty");
    }
    invalidReplayCaseService.saveInvalidCase(replayId);
    return CommonResponse.successResponse("ok", null);
  }

  private CommonResponse createPlan(BuildReplayPlanRequest request) {
    if (request == null) {
      return CommonResponse.badResponse("The request empty not allowed");
    }
    try {
      MDCTracer.addAppId(request.getAppId());
      return planProduceService.createPlan(request);
    } catch (Throwable e) {
      LOGGER.error("create plan error: {} , request: {}", e.getMessage(), request, e);
      progressEvent.onReplayPlanCreateException(request, e);

      if (e instanceof PlanRunningException) {
        PlanRunningException PlanRunningException = (PlanRunningException) e;
        return CommonResponse.badResponse(PlanRunningException.getMessage(),
            new BuildReplayPlanResponse(PlanRunningException.getCode()));
      } else {
        return CommonResponse.badResponse("create plan error！" + e.getMessage(),
            new BuildReplayPlanResponse(BuildReplayFailReasonEnum.UNKNOWN));
      }

    } finally {
      MDCTracer.clear();
      planProduceService.removeCreating(request.getAppId(), request.getTargetEnv());
    }
  }

  @Data
  private static class ProgressStatus {

    double percent;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Date lastUpdateTime;
  }
}