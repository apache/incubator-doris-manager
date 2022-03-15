import { InitializeAPI } from '@src/routes/initialize/initialize.api';
import { isSuccess } from '@src/utils/http';
import { Form, Radio, Input, Button, message } from 'antd';
import { useForm } from 'antd/lib/form/Form';
import React from 'react';
import { RouteProps, useHistory, useRouteMatch } from 'react-router';
import styles from './admin-user.less';
interface AdminUserProps extends RouteProps {}

export function AdminUser(props: AdminUserProps) {
    const [form] = useForm();
    const history = useHistory();
    const match = useRouteMatch();
    async function onFinish(values: any) {
        const { password_confirm, username, ...params } = values;
        const res = await InitializeAPI.setAdmin({ ...params, name: username });
        if (isSuccess(res)) {
            history.push('/initialize/auth/studio/finish');
        } else {
            message.error(res.msg);
        }
    }

    return (
        <div className={styles['admin-user']}>
            <Form
                name="basic"
                layout="vertical"
                onFinish={onFinish}
                //   onFinishFailed={onFinishFailed}
                autoComplete="off"
            >
                <Form.Item
                    label="Admin用户名"
                    tooltip="用于访问Palo Studio最高级管理员权限"
                    name="username"
                    rules={[
                        { required: true, message: '请输入Admin用户名' },
                        { max: 19, message: '用户名长度应小于20' },
                        {
                            pattern: /^[a-zA-Z0-9]+$/,
                            message: '用户名只能包含大小写字母以及数字',
                        },
                    ]}
                >
                    <Input placeholder="请输入Admin用户名" />
                </Form.Item>

                <Form.Item
                    name="email"
                    rules={[
                        {
                            type: 'email',
                            message: '请输入正确的邮箱地址',
                        },
                    ]}
                    label="Admin邮箱"
                    tooltip="用于访问Palo Studio最高级管理员权限"
                >
                    <Input placeholder="请输入Admin邮箱" />
                </Form.Item>
                <Form.Item
                    name="password"
                    rules={[
                        { required: true, message: '请输入密码' },
                        { min: 6, max: 12, message: '密码长度为6-12位' },
                        {
                            pattern:
                                /^(?![a-zA-Z]+$)(?![A-Z\d]+$)(?![A-Z_]+$)(?![a-z\d]+$)(?![a-z_]+$)(?![\d_]+$)[a-zA-Z\d_]+$/,
                            message: '至少包含英文大写、英文小写、数字和下划线中的3种',
                        },
                    ]}
                    label="密码"
                    required
                >
                    <Input type="password" placeholder="请输入密码" />
                </Form.Item>
                <Form.Item
                    name="password_confirm"
                    rules={[
                        {
                            required: true,
                            message: '请再次确认密码',
                        },
                        ({ getFieldValue }) => ({
                            validator(_, value) {
                                if (!value || getFieldValue('password') === value) {
                                    return Promise.resolve();
                                }
                                return Promise.reject(new Error('两次输入密码不一致'));
                            },
                        }),
                    ]}
                    label="确认密码"
                >
                    <Input type="password" placeholder="请再次确认密码" />
                </Form.Item>
                <Form.Item>
                    <Button type="primary" htmlType="submit">
                        保存
                    </Button>
                </Form.Item>
            </Form>
        </div>
    );
}