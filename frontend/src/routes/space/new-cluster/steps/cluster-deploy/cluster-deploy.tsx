import React, { useContext, useEffect, useState } from 'react';
import { PageContainer } from '@ant-design/pro-layout';
import ProCard from '@ant-design/pro-card';
import { Button, Row, Space, Table, Tabs, message, Steps } from 'antd';
import { LoadingOutlined, PlayCircleFilled } from '@ant-design/icons';
import { useHistory } from 'react-router';
import { useRequest } from 'ahooks';
import TabPane from '@ant-design/pro-card/lib/components/TabPane';
import { DorisNodeTypeEnum } from '../../types/params.type';
import { IResult } from '@src/interfaces/http.interface';
import { SpaceAPI } from '@src/routes/space/space.api';
import { OperateStatusEnum } from '@src/routes/space/space.data';
import { isSuccess } from '@src/utils/http';
import { NewSpaceInfoContext } from '@src/common/common.context';
import { useDidCache, useDidRecover } from 'react-router-cache-route';
import { useRecoilState } from 'recoil';
import { stepDisabledState } from '@src/routes/space/access-cluster/access-cluster.recoil';

const { Step } = Steps;

const ERROR_STATUS = [OperateStatusEnum.FAIL, OperateStatusEnum.CANCEL];

const STEP_MAP = {
    GET_INSTALL_PACKAGE: '获取安装包',
    NODE_CONF: '下发节点配置',
    START_UP_NODE: '启动节点',
};

export function ClusterDeploy(props: any) {
    const { reqInfo, step } = useContext(NewSpaceInfoContext);
    const history = useHistory();
    const [activeKey, setActiveKey] = useState(DorisNodeTypeEnum.FE);
    const [instances, setInstances] = useState<any[]>([]);
    const [stepDisabled, setStepDisabled] = useRecoilState(stepDisabledState);
    const [readyLoading, setReadyLoading] = useState(true);
    const getJDBCReady = useRequest<IResult<any>, any>(
        (clusterId: string) => {
            return SpaceAPI.getJDBCReady<any>({ clusterId });
        },
        {
            manual: true,
            pollingInterval: 1000,
            onSuccess: (res: any) => {
                if (isSuccess(res)) {
                    const data: boolean = res.data;
                    if (data) {
                        getJDBCReady.cancel();
                        setReadyLoading(false);
                        setStepDisabled({ ...stepDisabled, next: false });
                    }
                }
            },
            onError: () => {
                if (reqInfo.cluster_id) {
                    message.error('请求出错');
                }
            },
        },
    );
    const getClusterInstances = useRequest<IResult<any>, any>(
        (clusterId: string) => {
            return SpaceAPI.getClusterInstance<any>({ clusterId });
        },
        {
            manual: true,
            pollingInterval: 2000,
            onSuccess: (res: any) => {
                if (isSuccess(res)) {
                    const data: any[] = res.data;
                    setInstances(data);
                    if (data.some(item => ERROR_STATUS.includes(item.operateStatus))) {
                        getClusterInstances.cancel();
                    }
                    if (
                        data.every(item => item.operateStatus === OperateStatusEnum.SUCCESS && item.operateStage === 3)
                    ) {
                        getClusterInstances.cancel();
                        getJDBCReady.run(reqInfo.cluster_id)
                    }
                }
            },
            onError: () => {
                if (reqInfo.cluster_id) {
                    message.error('请求出错');
                }
            },
        },
    );

    useDidCache(() => {
        getClusterInstances.cancel();
        getClusterInstances.cancel();
    });

    useDidRecover(() => {
        if (reqInfo.cluster_id && step === 6) {
            getClusterInstances.run(reqInfo.cluster_id);
        }
    });

    useEffect(() => {
        if (reqInfo.cluster_id && step === 6) {
            getClusterInstances.run(reqInfo.cluster_id);
        }
        return () => {
            getClusterInstances.cancel();
            getClusterInstances.cancel();
        };
    }, [reqInfo.cluster_id, step]);

    const getStepStatus = (record: any) => {
        const currentStepStatus = OperateStatusEnum.getStepStatus(record.operateStatus);
        if (currentStepStatus === 'error') return 'error';
        return readyLoading ? 'process' : 'finish';
    };

    const columns = [
        {
            title: '序号',
            dataIndex: 'instanceId',
        },
        {
            title: '节点IP',
            dataIndex: 'nodeHost',
        },
        {
            title: '安装进度',
            dataIndex: 'operateStage',
            render: (operateStage: number, record: any) => (
                <Steps
                    progressDot={(iconDot, { status }) => {
                        if (status === 'process') return <LoadingOutlined style={{ color: '#1890ff' }} />;
                        return iconDot;
                    }}
                    status={getStepStatus(record)}
                    current={record.operateStage - 1}
                    size="small"
                    style={{ marginLeft: -50 }}
                >
                    {Object.keys(STEP_MAP).map((stepKey, index) => (
                        <Step key={index} style={{ width: 80 }} title={STEP_MAP[stepKey]} />
                    ))}
                </Steps>
            ),
        },
    ];

    const getTableDataSource = (activeKey: string) => {
        return instances.filter(item => item.moduleName === activeKey.toLowerCase());
    };

    return (
        <PageContainer
            header={{
                title: <h2>部署集群</h2>,
            }}
        >
            <Tabs activeKey={activeKey} onChange={(key: any) => setActiveKey(key)} type="card">
                <TabPane tab="FE节点" key={DorisNodeTypeEnum.FE}></TabPane>
                <TabPane tab="BE节点" key={DorisNodeTypeEnum.BE}></TabPane>
                <TabPane tab="Broker节点" key={DorisNodeTypeEnum.BROKER}></TabPane>
            </Tabs>
            <Table columns={columns} dataSource={getTableDataSource(activeKey)} />
        </PageContainer>
    );
}